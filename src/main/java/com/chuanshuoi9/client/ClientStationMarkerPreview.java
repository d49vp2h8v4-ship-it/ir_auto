package com.chuanshuoi9.client;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ItemStationMarker;
import com.chuanshuoi9.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID, value = Side.CLIENT)
public class ClientStationMarkerPreview {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        ItemStack main = mc.player.getHeldItemMainhand();
        ItemStack off = mc.player.getHeldItemOffhand();
        ItemStack held = main.getItem() == ModItems.STATION_MARKER ? main : (off.getItem() == ModItems.STATION_MARKER ? off : ItemStack.EMPTY);
        if (held.isEmpty()) {
            return;
        }
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null || !tag.hasKey(ItemStationMarker.NBT_RAIL)) {
            return;
        }
        BlockPos rail = BlockPos.fromLong(tag.getLong(ItemStationMarker.NBT_RAIL));
        World world = mc.world;
        world.spawnParticle(EnumParticleTypes.REDSTONE, rail.getX() + 0.5, rail.getY() + 0.15, rail.getZ() + 0.5, 1.0, 0.0, 0.0);
        world.spawnParticle(EnumParticleTypes.REDSTONE, rail.getX() + 0.5, rail.getY() + 0.22, rail.getZ() + 0.5, 1.0, 0.0, 0.0);
    }
}

