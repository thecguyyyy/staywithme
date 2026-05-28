package com.thecguyyyy.staywithme.ai.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

public class LongTaskWorkflow {
    private final String id;
    private final List<WorkStep> steps;
    private int currentIndex;

    public LongTaskWorkflow(String id, List<WorkStep> steps) {
        this.id = id;
        this.steps = new ArrayList<>(steps);
        this.currentIndex = 0;
    }

    public String id() {
        return this.id;
    }

    public int currentIndex() {
        this.advancePastCompletedSteps();
        return Math.min(this.currentIndex, this.steps.size());
    }

    public int stepCount() {
        return this.steps.size();
    }

    public Optional<WorkStep> currentStep() {
        this.advancePastCompletedSteps();
        if (this.currentIndex >= this.steps.size()) {
            return Optional.empty();
        }
        return Optional.of(this.steps.get(this.currentIndex));
    }

    public boolean restoreCurrentIndex(int index) {
        if (index < 0 || index > this.steps.size()) {
            return false;
        }
        this.currentIndex = index;
        for (int i = 0; i < index; i++) {
            this.steps.get(i).done("restored before reload");
        }
        return true;
    }

    public void completeCurrent(String note) {
        this.currentStep().ifPresent(step -> {
            step.done(note);
            this.currentIndex++;
        });
    }

    public void failCurrent(String note) {
        this.currentStep().ifPresent(step -> step.failed(note));
    }

    public boolean isComplete() {
        return this.currentStep().isEmpty();
    }

    public boolean hasPendingStep(Predicate<WorkStep> predicate) {
        for (int i = this.currentIndex; i < this.steps.size(); i++) {
            WorkStep step = this.steps.get(i);
            if (step.status() != WorkStepStatus.DONE && predicate.test(step)) {
                return true;
            }
        }
        return false;
    }

    public OptionalInt requiredAmountFor(WorkStepType type, String target) {
        for (WorkStep step : this.steps) {
            if (step.type() == type && step.target().equals(target)) {
                return OptionalInt.of(step.amount());
            }
        }
        return OptionalInt.empty();
    }

    public String summary() {
        Optional<WorkStep> current = this.currentStep();
        if (current.isEmpty()) {
            return this.id + ": complete";
        }
        return this.id + ": step " + (this.currentIndex + 1) + "/" + this.steps.size() + " " + current.get().summary();
    }

    private void advancePastCompletedSteps() {
        while (this.currentIndex < this.steps.size()
                && this.steps.get(this.currentIndex).status() == WorkStepStatus.DONE) {
            this.currentIndex++;
        }
    }
}
