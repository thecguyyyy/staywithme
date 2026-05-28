package com.thecguyyyy.staywithme.ai.workflow;

public class WorkStep {
    private final WorkStepType type;
    private final String target;
    private final int amount;
    private WorkStepStatus status;
    private String note;

    public WorkStep(WorkStepType type, String target, int amount) {
        this.type = type;
        this.target = target;
        this.amount = Math.max(0, amount);
        this.status = WorkStepStatus.WAITING;
        this.note = "";
    }

    public WorkStepType type() {
        return this.type;
    }

    public String target() {
        return this.target;
    }

    public int amount() {
        return this.amount;
    }

    public WorkStepStatus status() {
        return this.status;
    }

    public void running(String note) {
        this.status = WorkStepStatus.RUNNING;
        this.note = note == null ? "" : note;
    }

    public void done(String note) {
        this.status = WorkStepStatus.DONE;
        this.note = note == null ? "" : note;
    }

    public void failed(String note) {
        this.status = WorkStepStatus.FAILED;
        this.note = note == null ? "" : note;
    }

    public String summary() {
        StringBuilder builder = new StringBuilder(this.type.name())
                .append(' ')
                .append(this.target)
                .append(" x")
                .append(this.amount)
                .append(' ')
                .append(this.status.name().toLowerCase());
        if (!this.note.isBlank()) {
            builder.append(" (").append(this.note).append(')');
        }
        return builder.toString();
    }
}
