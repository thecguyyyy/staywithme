package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.TaskCatalogue;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PlayerEngineCatalogueDiagnostics {
    private PlayerEngineCatalogueDiagnostics() {
    }

    public static String resolveSummary(String rawName) {
        List<String> candidates = PlayerEngineCatalogueNames.candidates(rawName);
        if (candidates.isEmpty()) {
            return "empty input";
        }
        for (String candidate : candidates) {
            if (TaskCatalogue.taskExists(candidate)) {
                return "resolved=" + candidate + ", candidates=" + candidates;
            }
        }
        return "unresolved, candidates=" + candidates + ", closest=" + closestMatches(rawName, 5);
    }

    public static boolean canResolve(String rawName) {
        for (String candidate : PlayerEngineCatalogueNames.candidates(rawName)) {
            if (TaskCatalogue.taskExists(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> search(String query, int limit) {
        String normalized = searchKey(query);
        return TaskCatalogue.resourceNames().stream()
                .filter(name -> normalized.isBlank() || name.toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.naturalOrder())
                .limit(Math.max(1, limit))
                .toList();
    }

    public static List<String> closestMatches(String query, int limit) {
        String normalized = searchKey(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        return TaskCatalogue.resourceNames().stream()
                .sorted(Comparator
                        .comparingInt((String name) -> distance(normalized, name.toLowerCase(Locale.ROOT)))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(Math.max(1, limit))
                .toList();
    }

    private static String searchKey(String query) {
        if (query == null) {
            return "";
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        int namespace = normalized.indexOf(':');
        if (namespace >= 0 && namespace + 1 < normalized.length()) {
            normalized = normalized.substring(namespace + 1);
        }
        List<String> candidates = PlayerEngineCatalogueNames.candidates(normalized);
        return candidates.isEmpty() ? normalized : candidates.get(0);
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                int insertion = current[j - 1] + 1;
                int deletion = previous[j] + 1;
                current[j] = Math.min(substitution, Math.min(insertion, deletion));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
