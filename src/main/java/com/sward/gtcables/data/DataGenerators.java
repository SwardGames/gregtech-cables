package com.sward.gtcables.data;

import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.data.client.ConnectorModelProvider;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GregTechCables.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators
{
	@SubscribeEvent
	public static void gatherData(GatherDataEvent e)
	{
		DataGenerator generator = e.getGenerator();

		generator.addProvider(e.includeClient(), new ConnectorModelProvider(generator.getPackOutput(), e.getExistingFileHelper()));
	}
}
