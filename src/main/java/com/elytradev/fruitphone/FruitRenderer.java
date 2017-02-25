/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Aesen 'unascribed' Vismea
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.elytradev.fruitphone;

import java.util.Collections;
import java.util.List;

import com.elytradev.fruitphone.client.render.Rendering;
import com.elytradev.fruitphone.proxy.ClientProxy;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.elytradev.probe.api.IProbeData;
import com.elytradev.probe.api.UnitDictionary;
import com.elytradev.probe.api.impl.SIUnit;
import com.elytradev.probe.api.impl.Unit;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Public API. Feel free to re-use the intentionally generic Fruit Phone
 * renderer for other things.
 * <p>
 * Breaking API or ABI compatibility will be avoided, though if neccessary will
 * still happen. Let unascribed know if you use this API so you can be informed
 * if it's going to get broken.
 * <p>
 * <i>Should</i> work when passed sizes that are not used by Fruit Phone itself,
 * though I obviously don't really test that. Anything that causes the renderer
 * to act weird on nonstandard sizes should be considered a bug.
 */
@SideOnly(Side.CLIENT)
public class FruitRenderer {

	public static class MultiDataSize {
		public final DataSize clamped;
		public final DataSize actual;
		public MultiDataSize(DataSize clamped, DataSize actual) {
			this.clamped = clamped;
			this.actual = actual;
		}
		
	}

	public static class DataSize {
		private int width;
		private int height;
		
		public int getWidth() {
			return width;
		}
		
		public int getHeight() {
			return height;
		}
		
		
		
		public void setWidthIfGreater(int width) {
			if (width > this.width) this.width = width;
		}
		
		public void addWidth(int width) {
			this.width += width;
		}
		
		public void setWidth(int width) {
			this.width = width;
		}
		
		
		
		public void setHeightIfGreater(int height) {
			if (height > this.height) this.height = height;
		}
		
		public void setHeight(int height) {
			this.height = height;
		}
		
		public void addHeight(int height) {
			this.height += height;
		}
	}

	private static final ResourceLocation SPINNER = new ResourceLocation("fruitphone", "textures/gui/spinner.png");
	private static final ResourceLocation SLOT = new ResourceLocation("fruitphone", "textures/gui/slot.png");
	
	// TODO entity support
	
	public static BlockPos currentDataPos;
	public static List<IProbeData> currentFormattedData;
	public static List<IProbeData> currentRawData;
	
	private static final Unit DUMMY_UNIT = new SIUnit("", "", 0, Unit.FORMAT_STANDARD, false);
	
	public static void renderAndSyncTarget(int width, int height, boolean lit) {
		DataSize preferred = calculateAndSyncTargetUnclamped(width, height, width, height);
		renderAndSyncTarget(width, height, lit, preferred);
	}
	public static void renderAndSyncTarget(int width, int height, boolean lit, DataSize preferred) {
		EntityPlayer player = Minecraft.getMinecraft().player;
		World world = Minecraft.getMinecraft().world;
		
		Vec3d eyes = player.getPositionEyes(ClientProxy.partialTicks);
		Vec3d look = player.getLook(ClientProxy.partialTicks);
		double dist = 4;
		Vec3d max = eyes.addVector(look.xCoord * dist, look.yCoord * dist, look.zCoord * dist);
		RayTraceResult rtr = player.world.rayTraceBlocks(eyes, max, false, false, false);
		
		if (rtr == null || rtr.typeOfHit != Type.BLOCK) return;
		
		BlockPos pos = rtr.getBlockPos();
		
		if (!Objects.equal(pos, currentDataPos)) {
			IBlockState state = world.getBlockState(pos);
			if (!state.getBlock().hasTileEntity(state)) {
				currentDataPos = pos;
				currentRawData = Collections.emptyList();
			} else {
				render(format(Collections.emptyList(), pos), width, height, lit, preferred);
				GlStateManager.enableBlend();
				GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
				GlStateManager.color(1, 1, 1);
				GlStateManager.translate(0, 0, 500);
				renderSpinner(0, 0);
				GlStateManager.translate(0, 0, -500);
				GlStateManager.disableBlend();
				return;
			}
		}
		if (currentRawData != null) {
			currentFormattedData = format(currentRawData, currentDataPos);
			currentRawData = null;
		}
		render(currentFormattedData, width, height, lit, preferred);
	}
	
	public static MultiDataSize calculateAndSyncTarget(int preferredWidth, int preferredHeight, int maxWidth, int maxHeight) {
		DataSize actual = calculateAndSyncTargetUnclamped(preferredWidth, preferredHeight, maxWidth, maxHeight);
		DataSize clamped = new DataSize();
		clamped.setWidth(Math.min(maxWidth, actual.getWidth()));
		clamped.setHeight(Math.min(maxHeight, actual.getHeight()));
		return new MultiDataSize(clamped, actual);
	}
	
	public static DataSize calculateAndSyncTargetUnclamped(int preferredWidth, int preferredHeight, int maxWidth, int maxHeight) {
		EntityPlayer player = Minecraft.getMinecraft().player;
		World world = Minecraft.getMinecraft().world;
		
		Vec3d eyes = player.getPositionEyes(ClientProxy.partialTicks);
		Vec3d look = player.getLook(ClientProxy.partialTicks);
		double dist = 4;
		Vec3d max = eyes.addVector(look.xCoord * dist, look.yCoord * dist, look.zCoord * dist);
		RayTraceResult rtr = player.world.rayTraceBlocks(eyes, max, false, false, false);
		
		if (rtr == null || rtr.typeOfHit != Type.BLOCK) return new DataSize();
		
		BlockPos pos = rtr.getBlockPos();
		
		if (!Objects.equal(pos, currentDataPos)) {
			IBlockState state = world.getBlockState(pos);
			if (!state.getBlock().hasTileEntity(state)) {
				currentDataPos = pos;
				currentRawData = Collections.emptyList();
			} else {
				return calculatePreferredDataSize(format(Collections.emptyList(), pos), preferredWidth, preferredHeight, maxWidth, maxHeight);
			}
		}
		if (currentRawData != null) {
			currentFormattedData = format(currentRawData, currentDataPos);
			currentRawData = null;
		}
		return calculatePreferredDataSize(currentFormattedData, preferredWidth, preferredHeight, maxWidth, maxHeight);
	}
	
	/**
	 * Formats the given data, making it better for display.
	 * <p>
	 * How this is done is intentionally left undefined. Do not rely on this
	 * method doing something specific, it is likely to change drastically in
	 * the future.
	 * @param data The IProbeData lines to clean
	 * @param src The BlockPos that this data came from
	 * @return The cleaned IProbeData lines
	 */
	public static List<IProbeData> format(List<IProbeData> data, BlockPos src) {
		List<IProbeData> newData = Lists.newArrayList();
		IBlockState b = Minecraft.getMinecraft().world.getBlockState(src);
		ItemStack pickblock = b.getBlock().getPickBlock(b, Minecraft.getMinecraft().objectMouseOver, Minecraft.getMinecraft().world, src, Minecraft.getMinecraft().player);
		FruitProbeData ident = new FruitProbeData();
		ident.withInventory(ImmutableList.of(pickblock));
		ident.withLabel(pickblock.getDisplayName());
		newData.add(ident);
		boolean first = true;
		for (IProbeData ipd : data) {
			if (first && ipd.hasBar() && !ident.hasBar() && (ipd.hasLabel() ? ipd.getBarUnit() == null : false) && !ipd.hasInventory()) {
				FruitProbeData nw = new FruitProbeData();
				ident.withBar(ipd.getBarMinimum(), ipd.getBarCurrent(), ipd.getBarMaximum(), ipd.getBarUnit());
				if (ipd.hasLabel()) {
					if (ipd.getBarUnit() == null) {
						ident.setBarLabel(ipd.getLabel().getFormattedText());
					} else {
						nw.withLabel(ipd.getLabel());
					}
				}
				if (ipd.hasInventory()) {
					nw.withInventory(ipd.getInventory());
				}
				newData.add(nw);
			} else {
				newData.add(ipd);
			}
			first = false;
		}
		return newData;
	}
	
	public static DataSize calculatePreferredDataSize(List<IProbeData> data, int preferredWidth, int preferredHeight, int maxWidth, int maxHeight) {
		DataSize ds = new DataSize();
		int x = 0;
		int y = 0;
		int slots = 0;
		for (IProbeData d : data) {
			int lineSize = 0;
			y += 2;
			boolean renderLabel = true;
			if (d.hasInventory() && !d.getInventory().isEmpty()) {
				if (d.getInventory().size() == 1 && (d.hasLabel() || d.hasBar())) {
					ds.setWidthIfGreater(x+16);
					y -= 2;
					x += 20;
					lineSize = Math.max(lineSize, 16);
				}
			}
			if (d.hasBar()) {
				String str;
				if (d instanceof FruitProbeData && ((FruitProbeData) d).getBarLabel() != null) {
					str = ((FruitProbeData) d).getBarLabel();
				} else if (d.hasLabel() && d.getBarUnit() == null) {
					str = d.getLabel().getFormattedText();
					renderLabel = false;
				} else {
					str = d.getBarUnit() == null ? Unit.FORMAT_STANDARD.format(d.getBarCurrent()) : d.getBarUnit().format(d.getBarCurrent());
				}
				
				ds.setWidthIfGreater(preferredWidth);
				ds.setWidthIfGreater(x+4+(Minecraft.getMinecraft().fontRenderer.getStringWidth(str)));
				
				lineSize = Math.max(lineSize, d.hasLabel() ? 22 : 11);
			}
			if (renderLabel && d.hasLabel()) {
				ds.setWidthIfGreater(x+(Minecraft.getMinecraft().fontRenderer.getStringWidth(d.getLabel().getFormattedText())));
				lineSize = Math.max(lineSize, 8);
			}
			if (d.hasInventory() && ((!d.hasBar() && !d.hasLabel()) || d.getInventory().size() > 1)) {
				y += lineSize+2;
				if (d.getInventory().size() == 9) {
					ds.setWidthIfGreater(18*3);
					lineSize = 18*3;
				} else {
					slots += d.getInventory().size();
				}
			}
			y += lineSize;
			x = 0;
		}
		ds.setWidthIfGreater(x);
		ds.setHeightIfGreater(y);
		int slotsPerRow = Math.min(9, maxWidth/18);
		ds.setWidthIfGreater(x + (slots >= slotsPerRow ? 18*slotsPerRow : 18*slots));
		ds.addHeight(2+(slots/slotsPerRow)*18);
		if (slots % slotsPerRow > 0) {
			ds.addHeight(18);
		}
		return ds;
	}
	
	public static float getContainScale(int canvasWidth, int canvasHeight, int dataWidth, int dataHeight) {
		// no need to scale if the data fits
		if (dataWidth <= canvasWidth && dataHeight <= canvasHeight) return 1;
		return Math.min(((float)canvasWidth)/((float)dataWidth), ((float)canvasHeight)/((float)dataHeight));
	}
	
	public static void render(List<IProbeData> data, int width, int height, int maxWidth, int maxHeight, boolean glasses) {
		DataSize preferred = calculatePreferredDataSize(data, width, height, maxWidth, maxHeight);
		render(data, width, height, glasses, preferred);
	}
		
	public static void render(List<IProbeData> data, int width, int height, boolean glasses, DataSize preferred) {
		GlStateManager.pushMatrix();
		float contain = getContainScale(width, height, preferred.width, preferred.height);
		
		if (!glasses) {
			preferred = calculatePreferredDataSize(data, (int)(width/contain), (int)(height/contain), (int)(width/contain), (int)(height/contain));
			contain = getContainScale(width, height, preferred.width, preferred.height);
		}
		
		if (Minecraft.getMinecraft().gameSettings.showDebugInfo) {
			Gui.drawRect(0, 0, width, height, 0xFF00FF00);
			GlStateManager.translate(0, 0, 40);
		}
		GlStateManager.scale(contain, contain, 1);
		if (Minecraft.getMinecraft().gameSettings.showDebugInfo) {
			Gui.drawRect(0, 0, preferred.width, preferred.height, 0xAAFF0000);
			GlStateManager.translate(0, 0, 40);
		}
		
		int actualWidth = glasses ? width : (int) (width/contain);
		
		
		int x = 0;
		int y = 0;
		for (IProbeData d : data) {
			int lineSize = 0;
			int textPosY = y+2;
			y += 2;
			boolean renderLabel = true;
			if (d.hasInventory() && !d.getInventory().isEmpty()) {
				if (glasses) RenderHelper.enableGUIStandardItemLighting();
				if (d.getInventory().size() == 1 && (d.hasLabel() || d.hasBar())) {
					Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(d.getInventory().get(0), x, y-2);
					Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer, d.getInventory().get(0), x, y-2, "");
					x += 20;
					if (d.hasBar()) {
						textPosY -= 2;
					} else {
						textPosY += 2;
					}
					lineSize = Math.max(lineSize, 16);
				}
				RenderHelper.disableStandardItemLighting();
			}
			if (d.hasBar()) {
				int barY = textPosY;
				if (d.hasLabel()) {
					barY += 10;
				}
				
				double maxNormalized = d.getBarMaximum()-d.getBarMinimum();
				double currentNormalized = d.getBarCurrent()-d.getBarMinimum();
				double zero = (d.getBarMinimum() < 0 ? -d.getBarMinimum() : 0);
				
				double startX = (x+1+((zero/maxNormalized)*((actualWidth-x)-2)));
				double endX = (x+1+((currentNormalized/maxNormalized)*((actualWidth-x)-2)));
				
				if (startX < x+1) {
					startX = x+1;
				} else if (startX > actualWidth-1) {
					startX = actualWidth-1;
				}
				
				if (endX > actualWidth-1) {
					endX = actualWidth-1;
				} else if (endX < x+1) {
					endX = x+1;
				}
				
				int color = d.getBarUnit() == null ? 0xFFAAAAAA : d.getBarUnit().getBarColor()|0xFF000000;
				
				Rendering.drawRect(x, barY, actualWidth, barY+11, -1);
				GlStateManager.translate(0, 0, 40);
				Rendering.drawRect(x+1, barY+1, actualWidth-1, barY+10, 0xFF000000);
				GlStateManager.translate(0, 0, 40);
				if (d.getBarUnit() != null && UnitDictionary.getInstance().isFluid(d.getBarUnit())) {
					Fluid f = UnitDictionary.getInstance().getFluid(d.getBarUnit());
					ResourceLocation tex = f.getStill(new FluidStack(f, (int)(d.getBarCurrent()*1000)));
					TextureAtlasSprite tas = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tex.toString());
					Rendering.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
					int segments = (int)((endX-startX) / 16);
					for (int i = 0; i < segments; i++) {
						Rendering.drawTexturedRect(startX+(i*16), barY+1, startX+((i+1)*16), barY+10, tas);
					}
					Rendering.drawTexturedRect(startX+(segments*16), barY+1, endX, barY+10, tas);
				} else {
					Rendering.drawRect(startX, barY+1, endX, barY+10, color);
				}
				
				GlStateManager.translate(0, 0, 40);
				String str;
				if (d instanceof FruitProbeData && ((FruitProbeData) d).getBarLabel() != null) {
					str = ((FruitProbeData) d).getBarLabel();
				} else if (d.hasLabel() && d.getBarUnit() == null) {
					str = d.getLabel().getFormattedText();
					renderLabel = false;
				} else {
					str = d.getBarUnit() == null ? Unit.FORMAT_STANDARD.format(d.getBarCurrent()) : d.getBarUnit().format(d.getBarCurrent());
				}
				FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
						GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
				fr.drawString(str, (actualWidth-1)-fr.getStringWidth(str), barY+2, -1, false);
				GlStateManager.disableBlend();
				
				lineSize = Math.max(lineSize, d.hasLabel() ? 22 : 11);
			}
			if (renderLabel && d.hasLabel()) {
				Minecraft.getMinecraft().fontRenderer.drawString(d.getLabel().getFormattedText(), x, textPosY, -1, false);
				lineSize = Math.max(lineSize, 8);
			}
			if (d.hasInventory() && ((!d.hasBar() && !d.hasLabel()) || d.getInventory().size() > 1)) {
				y += lineSize+2;
				lineSize = 0;
				GlStateManager.enableBlend();
				GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
				int perRow = d.getInventory().size() == 9 ? 3 : Math.min(9, actualWidth/18);
				int i = 0;
				for (ItemStack is : d.getInventory()) {
					if (is == null) is = ItemStack.EMPTY;
					Rendering.bindTexture(SLOT);
					GlStateManager.color(1, 1, 1);
					Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 18, 18, 18, 18);
					if (glasses) RenderHelper.enableGUIStandardItemLighting();
					int count = is.getCount();
					Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(is, x+1, y+1);
					Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer, is, x+1, y+1, count >= 100 ? "" : null);
					RenderHelper.disableStandardItemLighting();
					if (count >= 100) {
						GlStateManager.pushMatrix(); {
							GlStateManager.scale(0.5f, 0.5f, 1);
							GlStateManager.translate(0, 0, 400);
							String str = DUMMY_UNIT.format(count);
							FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
							fr.drawStringWithShadow(str, ((x*2)+34)-fr.getStringWidth(str), ((y*2)+34)-fr.FONT_HEIGHT, -1);
						} GlStateManager.popMatrix();
					}
					x += 18;
					i++;
					if (i >= perRow) {
						i = 0;
						x = 0;
						y += 18;
					}
				}
				if (i > 0) {
					lineSize = 18;
				}
			}
			y += lineSize;
			x = 0;
		}
		GlStateManager.popMatrix();
	}

	public static void renderSpinner(int x, int y) {
		Rendering.bindTexture(SPINNER);
		int tocks = (int)(ClientProxy.ticksConsiderPaused/2);
		Gui.drawModalRectWithCustomSizedTexture(x, y, 16*tocks, 0, 16, 16, 96, 16);
	}

	
}

