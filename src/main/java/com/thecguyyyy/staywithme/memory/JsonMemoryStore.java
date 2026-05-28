package com.thecguyyyy.staywithme.memory;

import com.thecguyyyy.staywithme.StayWithMeMod;
import com.thecguyyyy.staywithme.util.JsonUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class JsonMemoryStore {
    private static final Path MEMORY_DIR = FMLPaths.CONFIGDIR.get().resolve("staywithme").resolve("memory");
    private static final Path EXPORT_DIR = FMLPaths.CONFIGDIR.get().resolve("staywithme").resolve("memory_exports");
    private static final Path IMPORT_DIR = FMLPaths.CONFIGDIR.get().resolve("staywithme").resolve("memory_imports");

    private JsonMemoryStore() {
    }

    public static FriendMemory load(UUID playerUuid, String playerName) {
        Path path = pathFor(playerUuid);
        if (!path.toFile().exists()) {
            return FriendMemory.create(playerUuid.toString(), playerName);
        }
        try {
            FriendMemory memory = JsonUtils.readJson(path, FriendMemory.class);
            if (memory == null) {
                return FriendMemory.create(playerUuid.toString(), playerName);
            }
            memory.normalize(playerUuid.toString(), playerName);
            return memory;
        } catch (IOException | RuntimeException error) {
            StayWithMeMod.LOGGER.warn("Failed to read StayWithMe memory for {}", playerUuid, error);
            return FriendMemory.create(playerUuid.toString(), playerName);
        }
    }

    public static void save(FriendMemory memory) {
        if (memory == null || memory.playerUuid == null || memory.playerUuid.isBlank()) {
            return;
        }
        try {
            JsonUtils.writeJson(pathFor(UUID.fromString(memory.playerUuid)), memory);
        } catch (IOException | IllegalArgumentException error) {
            StayWithMeMod.LOGGER.warn("Failed to write StayWithMe memory for {}", memory.playerUuid, error);
        }
    }

    public static void appendConversation(UUID playerUuid, String playerName, String summary) {
        FriendMemory memory = load(playerUuid, playerName);
        memory.addConversation(summary);
        save(memory);
    }

    public static void appendTask(UUID playerUuid, String playerName, String summary) {
        FriendMemory memory = load(playerUuid, playerName);
        memory.addTask(summary);
        save(memory);
    }

    public static void appendPortableNote(UUID playerUuid, String playerName, String note) {
        FriendMemory memory = load(playerUuid, playerName);
        memory.addPortableNote(note);
        save(memory);
    }

    public static void rememberResource(UUID playerUuid, String playerName, String resourceId, String hint, String source) {
        FriendMemory memory = load(playerUuid, playerName);
        memory.rememberResource(resourceId, hint, source);
        save(memory);
    }

    public static void updateExpedition(
            UUID playerUuid,
            String playerName,
            String resourceId,
            String dimension,
            Consumer<ExpeditionMemory> updater
    ) {
        FriendMemory memory = load(playerUuid, playerName);
        memory.updateExpedition(resourceId, dimension, updater);
        save(memory);
    }

    public static Optional<ExpeditionMemory> findExpedition(
            UUID playerUuid,
            String playerName,
            String resourceId,
            String dimension
    ) {
        FriendMemory memory = load(playerUuid, playerName);
        return memory.findExpedition(resourceId, dimension);
    }

    public static Path exportPortable(UUID playerUuid, String playerName) throws IOException {
        FriendMemory memory = load(playerUuid, playerName);
        memory.normalize(playerUuid.toString(), playerName);
        String playerPart = safeFilePart(playerName);
        Path target = EXPORT_DIR.resolve(playerPart + "-" + playerUuid + "-companion-memory.json");
        JsonUtils.writeJson(target, memory);
        return target;
    }

    public static FriendMemory importPortable(UUID playerUuid, String playerName, String fileName) throws IOException {
        String safeFileName = safeImportFileName(fileName);
        Path source = IMPORT_DIR.resolve(safeFileName);
        FriendMemory imported = JsonUtils.readJson(source, FriendMemory.class);
        if (imported == null) {
            throw new IOException("Imported memory file is empty.");
        }
        imported.normalize(playerUuid.toString(), playerName);
        imported.playerUuid = playerUuid.toString();
        imported.playerName = playerName;
        imported.updatedAtEpochMillis = System.currentTimeMillis();
        save(imported);
        return imported;
    }

    public static Path importDirectory() {
        return IMPORT_DIR;
    }

    private static Path pathFor(UUID playerUuid) {
        return MEMORY_DIR.resolve(playerUuid + ".json");
    }

    private static String safeFilePart(String value) {
        String normalized = value == null ? "player" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9._-]", "_");
        return normalized.isBlank() ? "player" : normalized;
    }

    private static String safeImportFileName(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("Missing memory file name.");
        }
        String trimmed = fileName.trim();
        if (!trimmed.matches("[A-Za-z0-9._-]+\\.json")) {
            throw new IOException("Memory import file name must be a simple .json file name.");
        }
        return trimmed;
    }
}
