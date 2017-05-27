/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 William Thompson (unascribed)
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

package com.elytradev.fruitphone.vanilla;

import java.util.List;

import com.elytradev.probe.api.IProbeData;
import com.elytradev.probe.api.UnitDictionary;
import com.elytradev.probe.api.impl.ProbeData;

import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ChatComponentTranslation;

public class FurnaceDataProvider implements VanillaDataProvider<TileEntityFurnace> {
	
	@Override
	public void provideProbeData(TileEntityFurnace te, List<IProbeData> li) {
		int furnaceBurnTime = te.furnaceBurnTime;
		int currentItemBurnTime = te.currentItemBurnTime;
		int cookTime = te.furnaceCookTime;
		int totalCookTime = 200;

		li.add(new ProbeData()
				.withLabel(new ChatComponentTranslation("fruitphone.furnace.fuel"))
				.withBar(0, furnaceBurnTime, currentItemBurnTime, UnitDictionary.TICKS));
		li.add(new ProbeData()
				.withLabel(new ChatComponentTranslation("fruitphone.furnace.progress"))
				.withBar(0, totalCookTime == 0 ? 0 : (cookTime/(float)totalCookTime)*100, 100, UnitDictionary.PERCENT));
	}

}
