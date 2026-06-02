package com.thecguyyyy.staywithme.llm;

import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MiningExpeditionPlan {
    public String resourceId;
    public int amount;
    public String targetDimension;
    public int preferredYMin;
    public int preferredYMax;
    public String strategyMode;
    public String requiredTool;
    public String preparation;
    public List<String> executionActions = new ArrayList<>();
    public List<String> safetyRules = new ArrayList<>();
    public List<String> resupplyTriggers = new ArrayList<>();
    public String reason;
    public String confidence;
    public String source;

    public static MiningExpeditionPlan fallback(String resourceId, int amount, String reason) {
        String normalized = resourceId == null || resourceId.isBlank() ? "unknown" : resourceId;
        MiningExpeditionPlan plan = new MiningExpeditionPlan();
        plan.resourceId = normalized;
        plan.amount = Math.max(1, amount);
        MiningTargetRegistry.find(normalized).ifPresentOrElse(target -> {
            MiningTargetRegistry.ExplorationProfile profile = target.explorationProfile();
            plan.targetDimension = profile.dimension();
            plan.preferredYMin = profile.preferredYMin();
            plan.preferredYMax = profile.preferredYMax();
            plan.requiredTool = target.requiredToolHint();
        }, () -> {
            plan.targetDimension = "minecraft:overworld";
            plan.preferredYMin = -59;
            plan.preferredYMax = -53;
            plan.requiredTool = "iron pickaxe or better";
        });
        plan.strategyMode = "BRANCH_MINE";
        plan.preparation = "Ensure the companion has the required pickaxe, food/safety margin, and free inventory before mining.";
        plan.executionActions.add("PREPARE_REQUIRED_TOOL");
        plan.executionActions.add("MOVE_TO_SAFE_MINING_AREA");
        plan.executionActions.add("DESCEND_TO_TARGET_LAYER");
        plan.executionActions.add("MINE_TARGET_RESOURCE");
        plan.executionActions.add("RETURN_TO_OWNER_OR_SUPPLY_POINT");
        plan.safetyRules.add("Never mine straight down.");
        plan.safetyRules.add("Stop and return if health is low, inventory is nearly full, food is low without carried food, torches/tools run low, or nearby hostiles become dangerous.");
        plan.safetyRules.add("Avoid lava and keep a traversable route back.");
        plan.reason = reason == null || reason.isBlank() ? "Local fallback expedition plan." : reason;
        plan.confidence = "low";
        plan.source = "local_fallback";
        plan.normalize(normalized, amount, "local_fallback");
        return plan;
    }

    public void normalize(String fallbackResourceId, int fallbackAmount, String fallbackSource) {
        if (this.resourceId == null || this.resourceId.isBlank()) {
            this.resourceId = fallbackResourceId;
        }
        this.amount = Math.max(1, this.amount <= 0 ? fallbackAmount : this.amount);
        if (this.targetDimension == null || this.targetDimension.isBlank()) {
            this.targetDimension = "minecraft:overworld";
        }
        if (this.preferredYMin > this.preferredYMax) {
            int swap = this.preferredYMin;
            this.preferredYMin = this.preferredYMax;
            this.preferredYMax = swap;
        }
        if (this.strategyMode == null || this.strategyMode.isBlank()) {
            this.strategyMode = "BRANCH_MINE";
        }
        if (this.requiredTool == null || this.requiredTool.isBlank()) {
            this.requiredTool = "unknown";
        }
        if (this.preparation == null || this.preparation.isBlank()) {
            this.preparation = "Prepare the required tool chain before mining.";
        }
        if (this.executionActions == null) {
            this.executionActions = new ArrayList<>();
        }
        if (this.executionActions.isEmpty()) {
            this.executionActions.add("PREPARE_REQUIRED_TOOL");
            this.executionActions.add("MINE_TARGET_RESOURCE");
            this.executionActions.add("RETURN_TO_OWNER_OR_SUPPLY_POINT");
        }
        if (this.safetyRules == null) {
            this.safetyRules = new ArrayList<>();
        }
        if (this.safetyRules.isEmpty()) {
            this.safetyRules.add("Return when unsafe instead of improvising low-level movement.");
        }
        if (this.resupplyTriggers == null) {
            this.resupplyTriggers = new ArrayList<>();
        }
        this.addResupplyTrigger("health_below_35_percent");
        this.addResupplyTrigger("inventory_free_slots_lte_1");
        this.addResupplyTrigger("nearby_hostiles_gte_3");
        this.addResupplyTrigger("food_lte_8_without_carried_food");
        this.addResupplyTrigger("torches_lte_2");
        this.addResupplyTrigger("usable_pickaxe_durability_lte_8");
        if (this.reason == null || this.reason.isBlank()) {
            this.reason = "No reason provided.";
        }
        if (this.confidence == null || this.confidence.isBlank()) {
            this.confidence = "low";
        }
        if (this.source == null || this.source.isBlank()) {
            this.source = fallbackSource;
        }
    }

    public String memoryHint() {
        return "expeditionPlan"
                + "; mode=" + this.strategyMode
                + "; dimension=" + this.targetDimension
                + "; y=" + this.preferredYMin + ".." + this.preferredYMax
                + "; tool=" + this.requiredTool
                + "; preparation=" + this.preparation
                + "; safety=" + String.join(" / ", this.safetyRules)
                + "; resupply=" + String.join(" / ", this.resupplyTriggers);
    }

    public String source() {
        return this.source == null ? "unknown" : this.source;
    }

    public String summary() {
        return String.format(
                Locale.ROOT,
                "Mining plan %s x%d: mode=%s, dim=%s, y=%d..%d, tool=%s, prep=%s, safety=%s, confidence=%s",
                this.resourceId,
                this.amount,
                this.strategyMode,
                this.targetDimension,
                this.preferredYMin,
                this.preferredYMax,
                this.requiredTool,
                this.preparation,
                String.join(" / ", this.safetyRules),
                this.confidence
        );
    }

    private void addResupplyTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return;
        }
        for (String existing : this.resupplyTriggers) {
            if (trigger.equals(existing)) {
                return;
            }
        }
        this.resupplyTriggers.add(trigger);
    }
}
