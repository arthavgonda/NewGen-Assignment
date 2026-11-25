package com.newgen.los.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UtilityRun {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private Long runId;
    private String utilityName;
    private String triggerType;
    private LocalDate businessDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String result;
    private int applicationsProcessed;
    private int alertsCreated;
    private String outputFile;
    private String errorMessage;

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getUtilityName() {
        return utilityName;
    }

    public void setUtilityName(String utilityName) {
        this.utilityName = utilityName;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getApplicationsProcessed() {
        return applicationsProcessed;
    }

    public void setApplicationsProcessed(int applicationsProcessed) {
        this.applicationsProcessed = applicationsProcessed;
    }

    public int getAlertsCreated() {
        return alertsCreated;
    }

    public void setAlertsCreated(int alertsCreated) {
        this.alertsCreated = alertsCreated;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
