package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.network.StationSyncMessage.StationData;
import net.minecraft.util.math.BlockPos;

public interface StationPickerReceiver {
    void applyPickedStationPos(int stopIndex, BlockPos pos);

    void applyPickedStation(int stopIndex, StationData station);
}

