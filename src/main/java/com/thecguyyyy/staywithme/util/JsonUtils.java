package com.thecguyyyy.staywithme.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonUtils {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJsonObjectText(String text, Class<T> type) {
        return GSON.fromJson(extractFirstJsonObject(text), type);
    }

    public static <T> T readJson(Path path, Class<T> type) throws IOException {
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
    }

    public static void writeJson(Path path, Object value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, GSON.toJson(value), StandardCharsets.UTF_8);
    }

    public static String extractFirstJsonObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null.");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("No JSON object found in text: " + text);
        }
        return text.substring(start, end + 1);
    }
}
