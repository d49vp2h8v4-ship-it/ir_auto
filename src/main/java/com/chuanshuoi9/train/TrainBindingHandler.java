package com.chuanshuoi9.train;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ItemTrainControlPaper;
import com.chuanshuoi9.item.ModItems;
import com.chuanshuoi9.network.OpenTrainTimetableGuiMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID)
public class TrainBindingHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        if (!player.isRiding()) {
            return;
        }
        ItemStack stack = getControlPaperStack(player);
        if (stack.isEmpty()) {
            return;
        }
        Entity riding = player.getRidingEntity();
        if (riding == null || !IrTrainReflection.isIrTrainEntity(riding)) {
            return;
        }
        Entity bindTarget = IrTrainReflection.resolveControllableTrainForBinding(player, riding);
        if (bindTarget == null) {
            player.sendMessage(new TextComponentString("绑定失败：请将准星对准机车车体右键完成绑定"));
            player.dismountRidingEntity();
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(ItemTrainControlPaper.NBT_BOUND_TRAIN, bindTarget.getUniqueID().toString());
        TrainAutoPilotData.ensureAndGet(bindTarget);
        player.sendMessage(new TextComponentString("已右键绑定列车: " + bindTarget.getName()));
        player.dismountRidingEntity();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting()) {
            return;
        }
        if (!(event.getEntityMounting() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityMounting();
        ItemStack stack = getControlPaperStack(player);
        if (stack.isEmpty()) {
            return;
        }
        Entity mount = event.getEntityBeingMounted();
        Entity bindTarget = IrTrainReflection.resolveControllableTrainForBinding(player, mount);
        if (bindTarget == null) {
            if (mount != null && IrTrainReflection.isIrTrainEntity(mount)) {
                if (!player.world.isRemote) {
                    player.sendMessage(new TextComponentString("绑定失败：请将准星对准机车车体右键完成绑定"));
                }
                event.setCanceled(true);
            }
            return;
        }
        if (!player.world.isRemote) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setString(ItemTrainControlPaper.NBT_BOUND_TRAIN, bindTarget.getUniqueID().toString());
            TrainAutoPilotData.ensureAndGet(bindTarget);
            player.sendMessage(new TextComponentString("已右键绑定列车: " + bindTarget.getName()));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        EntityPlayer player = event.getEntityPlayer();
        Entity target = event.getTarget();
        ItemStack stack = getControlPaperStack(player);
        if (!stack.isEmpty()) {
            Entity bindTarget = IrTrainReflection.resolveControllableTrainForBinding(player, target);
            if (bindTarget == null) {
                if (IrTrainReflection.isIrTrainEntity(target)) {
                    if (!player.world.isRemote) {
                        player.sendMessage(new TextComponentString("绑定失败：请将准星对准机车车体右键完成绑定"));
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(EnumActionResult.SUCCESS);
                }
                return;
            }
            if (!player.world.isRemote) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    stack.setTagCompound(tag);
                }
                tag.setString(ItemTrainControlPaper.NBT_BOUND_TRAIN, bindTarget.getUniqueID().toString());
                TrainAutoPilotData.ensureAndGet(bindTarget);
                player.sendMessage(new TextComponentString("已右键绑定列车: " + bindTarget.getName()));
            }
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            return;
        }
        if (!IrTrainReflection.isIrControllableTrain(target)) {
            return;
        }
        return;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        EntityPlayer player = event.getEntityPlayer();
        Entity target = event.getTarget();
        ItemStack stack = getControlPaperStack(player);
        if (stack.isEmpty()) {
            return;
        }
        Entity bindTarget = IrTrainReflection.resolveControllableTrainForBinding(player, target);
        if (bindTarget == null) {
            if (IrTrainReflection.isIrTrainEntity(target)) {
                if (!player.world.isRemote) {
                    player.sendMessage(new TextComponentString("绑定失败：请将准星对准机车车体右键完成绑定"));
                }
                event.setCanceled(true);
                event.setCancellationResult(EnumActionResult.SUCCESS);
            }
            return;
        }
        if (!player.world.isRemote) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setString(ItemTrainControlPaper.NBT_BOUND_TRAIN, bindTarget.getUniqueID().toString());
            TrainAutoPilotData.ensureAndGet(bindTarget);
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    private static ItemStack getControlPaperStack(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (isControlPaper(main)) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (isControlPaper(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isControlPaper(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null || stack.getItem().getRegistryName() == null) {
            return false;
        }
        if (stack.getItem() == ModItems.TRAIN_CONTROL_PAPER) {
            return true;
        }
        return (IrAutoMod.MODID + ":train_control_paper").equals(stack.getItem().getRegistryName().toString());
    }

    public static boolean openTrainGuiFromPaper(EntityPlayer player, String uuid) {
        if (player.world.isRemote || uuid == null || uuid.isEmpty()) {
            return false;
        }
        Entity target = IrTrainReflection.findBoundTrain(player, uuid);
        if (!IrTrainReflection.isIrControllableTrain(target)) {
            player.sendMessage(new TextComponentString("未找到已绑定列车"));
            return false;
        }
        NBTTagCompound payload = TrainAutoPilotData.snapshotForClient(target);
        IrAutoMod.NETWORK.sendTo(new OpenTrainTimetableGuiMessage(target.getUniqueID().toString(), payload), (net.minecraft.entity.player.EntityPlayerMP) player);
        return true;
    }
}
