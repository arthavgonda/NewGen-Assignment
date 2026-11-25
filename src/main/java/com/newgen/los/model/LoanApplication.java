package com.newgen.los.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LoanApplication {

    private Long applicationId;
    private String applicationNumber;
    private Long customerId;
    private String customerName;
    private Product product;
    private String branchCode;
    private String branchName;
    private BigDecimal requestedAmount;
    private Integer tenureMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal existingObligations;
    private String coBorrowerName;
    private BigDecimal annualInterestRate;
    private BigDecimal proposedEmi;
    private BigDecimal foirPercentage;
    private Boolean eligible;
    private String eligibilityReasons;
    private ApplicationStatus status;
    private BigDecimal approvedAmount;
    private String applicationFormPath;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private String reviewerCode;
    private String decisionReason;
    private LocalDateTime creditDecidedAt;
    private String creditApproverCode;

    public boolean hasCoBorrower() {
        return coBorrowerName != null && !coBorrowerName.isBlank();
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(Integer tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public BigDecimal getExistingObligations() {
        return existingObligations;
    }

    public void setExistingObligations(BigDecimal existingObligations) {
        this.existingObligations = existingObligations;
    }

    public String getCoBorrowerName() {
        return coBorrowerName;
    }

    public void setCoBorrowerName(String coBorrowerName) {
        this.coBorrowerName = coBorrowerName;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }

    public BigDecimal getProposedEmi() {
        return proposedEmi;
    }

    public void setProposedEmi(BigDecimal proposedEmi) {
        this.proposedEmi = proposedEmi;
    }

    public BigDecimal getFoirPercentage() {
        return foirPercentage;
    }

    public void setFoirPercentage(BigDecimal foirPercentage) {
        this.foirPercentage = foirPercentage;
    }

    public Boolean getEligible() {
        return eligible;
    }

    public void setEligible(Boolean eligible) {
        this.eligible = eligible;
    }

    public String getEligibilityReasons() {
        return eligibilityReasons;
    }

    public void setEligibilityReasons(String eligibilityReasons) {
        this.eligibilityReasons = eligibilityReasons;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public String getApplicationFormPath() {
        return applicationFormPath;
    }

    public void setApplicationFormPath(String applicationFormPath) {
        this.applicationFormPath = applicationFormPath;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getReviewerCode() {
        return reviewerCode;
    }

    public void setReviewerCode(String reviewerCode) {
        this.reviewerCode = reviewerCode;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public LocalDateTime getCreditDecidedAt() {
        return creditDecidedAt;
    }

    public void setCreditDecidedAt(LocalDateTime creditDecidedAt) {
        this.creditDecidedAt = creditDecidedAt;
    }

    public String getCreditApproverCode() {
        return creditApproverCode;
    }

    public void setCreditApproverCode(String creditApproverCode) {
        this.creditApproverCode = creditApproverCode;
    }
}
