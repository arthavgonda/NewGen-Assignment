package com.newgen.los.model;

import java.time.LocalDateTime;

public class HistoryEntry {

    private Long historyId;
    private Long applicationId;
    private HistoryAction action;
    private ApplicationStatus oldStatus;
    private ApplicationStatus newStatus;
    private String performedBy;
    private LocalDateTime performedAt;
    private String remarks;
    private String applicationNumber;
    private String customerName;

    public HistoryEntry() {
    }

    public HistoryEntry(Long applicationId, HistoryAction action, ApplicationStatus oldStatus,
                        ApplicationStatus newStatus, String performedBy, LocalDateTime performedAt, String remarks) {
        this.applicationId = applicationId;
        this.action = action;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
        this.remarks = remarks;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public HistoryAction getAction() {
        return action;
    }

    public void setAction(HistoryAction action) {
        this.action = action;
    }

    public ApplicationStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(ApplicationStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(ApplicationStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
}
