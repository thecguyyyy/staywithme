package com.thecguyyyy.staywithme.ai;

final class PlayerEngineStatusText {
    private PlayerEngineStatusText() {
    }

    static String shortStatus(String status, int maxLength) {
        if (status == null || status.isBlank()) {
            return "none";
        }
        if (status.length() <= maxLength) {
            return status;
        }
        return status.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
