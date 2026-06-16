package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.StayWithMeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompanionSkinTextures {
    private static final Set<String> REGISTERED_URLS = ConcurrentHashMap.newKeySet();

    private CompanionSkinTextures() {
    }

    public static ResourceLocation textureFor(UUID entityUuid, String skinUrl) {
        ResourceLocation fallback = DefaultPlayerSkin.getDefaultSkin(entityUuid);
        String normalizedUrl = normalizeUrl(skinUrl);
        if (normalizedUrl.isBlank()) {
            return fallback;
        }

        String hash = sha1(normalizedUrl);
        ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(StayWithMeMod.MOD_ID, "skins/" + hash);
        if (!REGISTERED_URLS.add(normalizedUrl)) {
            return textureId;
        }

        try {
            Minecraft minecraft = Minecraft.getInstance();
            File cacheFile = new File(new File(minecraft.gameDirectory, "staywithme-skins"), hash + ".png");
            File parent = cacheFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            minecraft.getTextureManager().register(textureId, new HttpTexture(cacheFile, normalizedUrl, fallback, true, null));
            return textureId;
        } catch (RuntimeException exception) {
            REGISTERED_URLS.remove(normalizedUrl);
            StayWithMeMod.LOGGER.warn("Failed to register companion skin texture {}", normalizedUrl, exception);
            return fallback;
        }
    }

    private static String normalizeUrl(String skinUrl) {
        if (skinUrl == null || skinUrl.isBlank()) {
            return "";
        }
        String trimmed = skinUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return "";
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return "";
            }
            return trimmed;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
