package com.thecguyyyy.staywithme.memory;

import java.util.ArrayList;
import java.util.List;

public class ResourceKnowledge {
    public String resourceId;
    public String acquisitionHint;
    public String requiredToolHint;
    public String dimensionHint;
    public String biomeHint;
    public String source;
    public double confidence;
    public long updatedAtEpochMillis;
    public List<String> observations = new ArrayList<>();

    public static ResourceKnowledge create(String resourceId, String hint, String source) {
        ResourceKnowledge knowledge = new ResourceKnowledge();
        knowledge.resourceId = resourceId;
        knowledge.acquisitionHint = hint;
        knowledge.source = source == null || source.isBlank() ? "unknown" : source;
        knowledge.confidence = 0.35D;
        knowledge.updatedAtEpochMillis = System.currentTimeMillis();
        knowledge.addObservation(hint);
        return knowledge;
    }

    public void normalize() {
        if (this.observations == null) {
            this.observations = new ArrayList<>();
        }
        if (this.source == null || this.source.isBlank()) {
            this.source = "unknown";
        }
        if (this.updatedAtEpochMillis <= 0L) {
            this.updatedAtEpochMillis = System.currentTimeMillis();
        }
    }

    public void reinforce(String hint, String source) {
        this.normalize();
        if (hint != null && !hint.isBlank()) {
            this.acquisitionHint = hint;
            this.addObservation(hint);
        }
        if (source != null && !source.isBlank()) {
            this.source = source;
        }
        this.confidence = Math.min(1.0D, Math.max(0.1D, this.confidence + 0.1D));
        this.updatedAtEpochMillis = System.currentTimeMillis();
    }

    public void addObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return;
        }
        this.normalize();
        this.observations.add(observation);
        while (this.observations.size() > 12) {
            this.observations.remove(0);
        }
    }

    public String summary() {
        StringBuilder builder = new StringBuilder(this.resourceId == null ? "unknown" : this.resourceId);
        if (this.acquisitionHint != null && !this.acquisitionHint.isBlank()) {
            builder.append(": ").append(this.acquisitionHint);
        }
        if (this.requiredToolHint != null && !this.requiredToolHint.isBlank()) {
            builder.append(" tool=").append(this.requiredToolHint);
        }
        if (this.dimensionHint != null && !this.dimensionHint.isBlank()) {
            builder.append(" dimension=").append(this.dimensionHint);
        }
        builder.append(" confidence=").append(String.format(java.util.Locale.ROOT, "%.2f", this.confidence));
        return builder.toString();
    }
}
