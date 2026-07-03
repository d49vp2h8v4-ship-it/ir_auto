package com.chuanshuoi9.train;

import com.chuanshuoi9.IrAutoMod;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID)
public class TrainAutoPilotTicker {
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        for (int i = 0; i < event.world.loadedEntityList.size(); i++) {
            Entity entity = event.world.loadedEntityList.get(i);
            if (entity == null || !IrTrainReflection.isIrControllableTrain(entity)) {
                continue;
            }
            TrainAutoPilotData.tickAutoDrive(entity);
        }
    }
}
