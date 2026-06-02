package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.network.LlmConfigSnapshotPacket;
import net.minecraft.client.Minecraft;

public final class StayWithMeClientConfigPacketHandler {
    private StayWithMeClientConfigPacketHandler() {
    }

    public static void handle(LlmConfigSnapshotPacket packet) {
        if (Minecraft.getInstance().screen instanceof StayWithMeLlmConfigScreen screen) {
            screen.applyServerSnapshot(packet);
        }
    }
}
