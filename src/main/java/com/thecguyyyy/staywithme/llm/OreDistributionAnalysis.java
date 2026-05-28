package com.thecguyyyy.staywithme.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OreDistributionAnalysis {
    public String resourceId;
    public String oreBlockId;
    public String dimension;
    public String yLevelHint;
    public String miningStrategy;
    public String requiredTool;
    public String safetyNotes;
    public String confidence;
    public String source;
    public List<String> steps = new ArrayList<>();

    public static OreDistributionAnalysis fallback(String resourceId, String hint) {
        OreDistributionAnalysis analysis = new OreDistributionAnalysis();
        analysis.resourceId = resourceId;
        analysis.oreBlockId = resourceId;
        analysis.dimension = "minecraft:overworld";
        analysis.yLevelHint = "unknown";
        analysis.miningStrategy = hint;
        analysis.requiredTool = "unknown";
        analysis.safetyNotes = "Use conservative survival mining and stop when unsafe.";
        analysis.confidence = "low";
        analysis.source = "local_fallback";
        analysis.steps.add(hint);
        analysis.normalize(resourceId, "local_fallback");
        return analysis;
    }

    public void normalize(String fallbackResourceId, String fallbackSource) {
        if (this.resourceId == null || this.resourceId.isBlank()) {
            this.resourceId = fallbackResourceId;
        }
        if (this.oreBlockId == null || this.oreBlockId.isBlank()) {
            this.oreBlockId = this.resourceId;
        }
        if (this.dimension == null || this.dimension.isBlank()) {
            this.dimension = "unknown";
        }
        if (this.yLevelHint == null || this.yLevelHint.isBlank()) {
            this.yLevelHint = "unknown";
        }
        if (this.miningStrategy == null || this.miningStrategy.isBlank()) {
            this.miningStrategy = "No known strategy yet.";
        }
        if (this.requiredTool == null || this.requiredTool.isBlank()) {
            this.requiredTool = "unknown";
        }
        if (this.safetyNotes == null || this.safetyNotes.isBlank()) {
            this.safetyNotes = "Avoid lava, falls, and hostile mobs.";
        }
        if (this.confidence == null || this.confidence.isBlank()) {
            this.confidence = "low";
        }
        if (this.source == null || this.source.isBlank()) {
            this.source = fallbackSource;
        }
        if (this.steps == null) {
            this.steps = new ArrayList<>();
        }
    }

    public String memoryHint() {
        return "ore=" + this.oreBlockId
                + "; dimension=" + this.dimension
                + "; y=" + this.yLevelHint
                + "; tool=" + this.requiredTool
                + "; strategy=" + this.miningStrategy
                + "; safety=" + this.safetyNotes;
    }

    public String source() {
        return this.source == null ? "unknown" : this.source;
    }

    public String summary() {
        String stepsText = this.steps == null || this.steps.isEmpty() ? "" : "; steps=" + String.join(" -> ", this.steps);
        return String.format(
                Locale.ROOT,
                "Ore analysis %s: block=%s, dimension=%s, y=%s, tool=%s, strategy=%s, confidence=%s%s",
                this.resourceId,
                this.oreBlockId,
                this.dimension,
                this.yLevelHint,
                this.requiredTool,
                this.miningStrategy,
                this.confidence,
                stepsText
        );
    }
}
