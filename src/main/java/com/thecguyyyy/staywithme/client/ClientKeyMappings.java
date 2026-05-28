package com.thecguyyyy.staywithme.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final KeyMapping OPEN_LLM_CONFIG = new KeyMapping(
            "key.staywithme.open_llm_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.staywithme"
    );

    private ClientKeyMappings() {
    }
}
