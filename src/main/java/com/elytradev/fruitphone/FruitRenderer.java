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

import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

import com.elytradev.fruitphone.proxy.ClientProxy;
import com.elytradev.fruitphone.proxy.Rendering;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;

import io.github.elytra.probe.api.IProbeData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.world.World;
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
	
	public static void renderAndSyncTarget(int width, int height, boolean lit) {
		DataSize preferred = calculateAndSyncTargetUnbounded(width, height);
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
		DataSize actual = calculateAndSyncTargetUnbounded(preferredWidth, preferredHeight);
		DataSize clamped = new DataSize();
		clamped.setWidth(Math.min(maxWidth, actual.getWidth()));
		clamped.setHeight(Math.min(maxHeight, actual.getHeight()));
		return new MultiDataSize(clamped, actual);
	}
	
	public static DataSize calculateAndSyncTargetUnbounded(int preferredWidth, int preferredHeight) {
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
				return calculatePreferredDataSize(format(Collections.emptyList(), pos), preferredWidth, preferredHeight);
			}
		}
		if (currentRawData != null) {
			currentFormattedData = format(currentRawData, currentDataPos);
			currentRawData = null;
		}
		return calculatePreferredDataSize(currentFormattedData, preferredWidth, preferredHeight);
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
			if (first && ipd.hasBar() && !ident.hasBar() && (ipd.hasLabel() ? Strings.isNullOrEmpty(ipd.getBarUnit()) : false) && !ipd.hasInventory()) {
				FruitProbeData nw = new FruitProbeData();
				ident.withBar(ipd.getBarMinimum(), ipd.getBarCurrent(), ipd.getBarMaximum(), ipd.getBarUnit());
				if (ipd.hasLabel()) {
					if (Strings.isNullOrEmpty(ipd.getBarUnit())) {
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
	
	public static DataSize calculatePreferredDataSize(List<IProbeData> data, int preferredWidth, int preferredHeight) {
		DataSize ds = new DataSize();
		int x = 0;
		int y = 0;
		boolean first = true;
		for (IProbeData d : data) {
			int lineSize = 0;
			if (first) {
				first = false;
			} else {
				y += 2;
			}
			boolean renderLabel = true;
			if (d.hasInventory() && !d.getInventory().isEmpty()) {
				if (d.getInventory().size() == 1) {
					ds.setWidthIfGreater(x+16);
					x += 20;
					lineSize = Math.max(lineSize, 16);
				}
				RenderHelper.disableStandardItemLighting();
			}
			if (d.hasBar()) {
				String str;
				if (d instanceof FruitProbeData && ((FruitProbeData) d).getBarLabel() != null) {
					str = ((FruitProbeData) d).getBarLabel();
				} else if (d.hasLabel() && Strings.isNullOrEmpty(d.getBarUnit())) {
					str = d.getLabel().getFormattedText();
					renderLabel = false;
				} else {
					str = d.getBarCurrent()+d.getBarUnit();
				}
				
				ds.setWidthIfGreater(preferredWidth);
				ds.setWidthIfGreater(x+4+(Minecraft.getMinecraft().fontRenderer.getStringWidth(str)));
				
				lineSize = Math.max(lineSize, d.hasLabel() ? 20 : 11);
			}
			if (renderLabel && d.hasLabel()) {
				ds.setWidthIfGreater(x+(Minecraft.getMinecraft().fontRenderer.getStringWidth(d.getLabel().getFormattedText())));
				lineSize = Math.max(lineSize, 8);
			}
			if (d.hasInventory() && d.getInventory().size() > 1) {
				y += lineSize;
				int perRow = 5;
				lineSize = IntMath.divide(d.getInventory().size(), perRow, RoundingMode.UP)*18;
				ds.setWidthIfGreater(Math.min(perRow, d.getInventory().size())*18);
			}
			y += lineSize;
			x = 0;
		}
		ds.setWidthIfGreater(x);
		ds.setHeightIfGreater(y);
		return ds;
	}
	
	public static float getContainScale(int canvasWidth, int canvasHeight, int dataWidth, int dataHeight) {
		// no need to scale if the data fits
		if (dataWidth <= canvasWidth && dataHeight <= canvasHeight) return 1;
		return Math.min(((float)canvasWidth)/((float)dataWidth), ((float)canvasHeight)/((float)dataHeight));
	}
	
	public static void render(List<IProbeData> data, int width, int height, boolean glasses) {
		DataSize preferred = calculatePreferredDataSize(data, width, height);
		render(data, width, height, glasses, preferred);
	}
		
	public static void render(List<IProbeData> data, int width, int height, boolean glasses, DataSize preferred) {
		GlStateManager.pushMatrix();
		float contain = getContainScale(width, height, preferred.width, preferred.height);
		
		/*
		Gui.drawRect(0, 0, width, height, 0xFF00FF00);
		GlStateManager.translate(0, 0, 40);
		*/
		GlStateManager.scale(contain, contain, 1);
		/*
		Gui.drawRect(0, 0, preferred.width, preferred.height, 0xAAFF0000);
		GlStateManager.translate(0, 0, 40);
		*/
		
		int actualWidth = glasses ? width : (int) (width/contain);
		
		int x = 0;
		int y = 0;
		boolean first = true;
		for (IProbeData d : data) {
			int lineSize = 0;
			int textPosY = y+2;
			if (first) {
				first = false;
			} else {
				y += 2;
			}
			boolean renderLabel = true;
			if (d.hasInventory() && !d.getInventory().isEmpty()) {
				if (glasses) RenderHelper.enableGUIStandardItemLighting();
				if (d.getInventory().size() == 1) {
					Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(d.getInventory().get(0), x, y);
					x += 20;
					textPosY += 2;
					lineSize = Math.max(lineSize, 16);
				}
				RenderHelper.disableStandardItemLighting();
			}
			if (d.hasBar()) {
				int barY = textPosY;
				if (d.hasLabel()) {
					textPosY -= 4;
					barY += 6;
				}
				
				float maxNormalized = d.getBarMaximum()-d.getBarMinimum();
				float currentNormalized = d.getBarCurrent()-d.getBarMinimum();
				float zero = (d.getBarMinimum() < 0 ? -d.getBarMinimum() : 0);
				
				float startX = (x+1+((zero/maxNormalized)*69));
				float endX = (x+1+((currentNormalized/maxNormalized)*((width-x)-1)));
				
				int color = getColorForUnit(d.getBarUnit());
				
				Rendering.drawRect(x, barY, width, barY+11, -1);
				GlStateManager.translate(0, 0, 40);
				Rendering.drawRect(x+1, barY+1, width-1, barY+10, 0xFF000000);
				GlStateManager.translate(0, 0, 40);
				Rendering.drawRect(startX, barY+1, endX, barY+10, color);
				
				GlStateManager.translate(0, 0, 40);
				String str;
				if (d instanceof FruitProbeData && ((FruitProbeData) d).getBarLabel() != null) {
					str = ((FruitProbeData) d).getBarLabel();
				} else if (d.hasLabel() && Strings.isNullOrEmpty(d.getBarUnit())) {
					str = d.getLabel().getFormattedText();
					renderLabel = false;
				} else {
					str = formatUnit(d.getBarCurrent(), d.getBarUnit());
				}
				FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
						GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
				fr.drawString(str, (width-1)-fr.getStringWidth(str), barY+2, -1, false);
				GlStateManager.disableBlend();
				
				lineSize = Math.max(lineSize, d.hasLabel() ? 20 : 11);
			}
			if (renderLabel && d.hasLabel()) {
				Minecraft.getMinecraft().fontRenderer.drawString(d.getLabel().getFormattedText(), x, textPosY, -1, false);
				lineSize = Math.max(lineSize, 8);
			}
			if (d.hasInventory() && d.getInventory().size() > 1) {
				y += lineSize;
				lineSize = 18;
				GlStateManager.enableBlend();
				GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
				for (ItemStack is : d.getInventory()) {
					if (is == null) is = ItemStack.EMPTY;
					Rendering.bindTexture(SLOT);
					GlStateManager.color(1, 1, 1);
					Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 18, 18, 18, 18);
					if (glasses) RenderHelper.enableGUIStandardItemLighting();
					Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(is, x+1, y+1);
					Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer, is, x+1, y+1, null);
					RenderHelper.disableStandardItemLighting();
					x += 18;
					if ((x+17) >= actualWidth) {
						x = 0;
						y += 18;
					}
				}
			}
			y += lineSize;
			x = 0;
		}
		GlStateManager.popMatrix();
	}
	
	public static int getColorForUnit(String barUnit) {
		if (barUnit.endsWith("/t")) {
			barUnit = barUnit.substring(0, barUnit.length()-2);
		}
		switch (barUnit) {
			case "FU":
			case "RF":
				return 0xFFAA0000;
			case "T":
				return 0xFF00AAFF;
			case "Dk":
				return 0xFF00AA00;
			case "mB":
				return 0xFF0000AA;
		}
		return 0xFFAAAAAA;
	}

	public static String formatUnit(int barCurrent, String barUnit) {
		int amt = barCurrent;
		String unit = barUnit;
		switch (unit) {
			case "d%":
				amt /= 10;
				unit = "%";
				break;
			case "c%":
				amt /= 100;
				unit = "%";
				break;
			case "m%":
				amt /= 1000;
				unit = "%";
				break;
		}
		return amt+unit;
	}

	public static void renderSpinner(int x, int y) {
		Rendering.bindTexture(SPINNER);
		int tocks = (int)(ClientProxy.ticksConsiderPaused/2);
		Gui.drawModalRectWithCustomSizedTexture(x, y, 16*tocks, 0, 16, 16, 96, 16);
	}

	
}

