/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.Objects;

/**
 * Flat document written to {@code optimize-reporting-metrics}. One document per process instance.
 * All fields are nullable so that partial updates (merge semantics) are supported across import
 * batches.
 */
public class ReportingMetricsDto implements OptimizeDto {

  private String processInstanceKey;
  private String processDefinitionKey;
  private String tenantId;

  /** Epoch-ms of the earliest Zeebe variable record seen for this PI. */
  private Long firstSeenAt;

  /** Epoch-ms of the latest Zeebe variable record seen for this PI. */
  private Long lastSeenAt;

  private String processLabel;
  private String startDate;
  private String endDate;

  private Double baselineCost;
  private Double llmCost;
  private Double automationCost;
  private Double totalCost;
  private Double valueCreated;

  private Integer agentTaskCount;
  private Integer humanTaskCount;
  private Integer autoTaskCount;
  private Long tokenUsage;

  private Integer errorCount;
  private Integer retryCount;
  private Integer processingTimeMs;
  private Integer queueWaitTimeMs;
  private Integer apiCallCount;
  private Integer complianceChecksPassed;

  private Double dataVolumeMb;
  private Double confidenceScore;
  private Double co2EmissionsKg;
  private Double customerSatisfactionScore;
  private Double fraudRiskScore;
  private Double externalServiceCostUsd;

  private Boolean slaBreached;
  private Boolean escalated;
  private Boolean manualOverride;

  public ReportingMetricsDto() {}

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public Long getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(final Long firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }

  public Long getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(final Long lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public String getProcessLabel() {
    return processLabel;
  }

  public void setProcessLabel(final String processLabel) {
    this.processLabel = processLabel;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(final String startDate) {
    this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(final String endDate) {
    this.endDate = endDate;
  }

  public Double getBaselineCost() {
    return baselineCost;
  }

  public void setBaselineCost(final Double baselineCost) {
    this.baselineCost = baselineCost;
  }

  public Double getLlmCost() {
    return llmCost;
  }

  public void setLlmCost(final Double llmCost) {
    this.llmCost = llmCost;
  }

  public Double getAutomationCost() {
    return automationCost;
  }

  public void setAutomationCost(final Double automationCost) {
    this.automationCost = automationCost;
  }

  public Double getTotalCost() {
    return totalCost;
  }

  public void setTotalCost(final Double totalCost) {
    this.totalCost = totalCost;
  }

  public Double getValueCreated() {
    return valueCreated;
  }

  public void setValueCreated(final Double valueCreated) {
    this.valueCreated = valueCreated;
  }

  public Integer getAgentTaskCount() {
    return agentTaskCount;
  }

  public void setAgentTaskCount(final Integer agentTaskCount) {
    this.agentTaskCount = agentTaskCount;
  }

  public Integer getHumanTaskCount() {
    return humanTaskCount;
  }

  public void setHumanTaskCount(final Integer humanTaskCount) {
    this.humanTaskCount = humanTaskCount;
  }

  public Integer getAutoTaskCount() {
    return autoTaskCount;
  }

  public void setAutoTaskCount(final Integer autoTaskCount) {
    this.autoTaskCount = autoTaskCount;
  }

  public Long getTokenUsage() {
    return tokenUsage;
  }

  public void setTokenUsage(final Long tokenUsage) {
    this.tokenUsage = tokenUsage;
  }

  public Integer getErrorCount() {
    return errorCount;
  }

  public void setErrorCount(final Integer errorCount) {
    this.errorCount = errorCount;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(final Integer retryCount) {
    this.retryCount = retryCount;
  }

  public Integer getProcessingTimeMs() {
    return processingTimeMs;
  }

  public void setProcessingTimeMs(final Integer processingTimeMs) {
    this.processingTimeMs = processingTimeMs;
  }

  public Integer getQueueWaitTimeMs() {
    return queueWaitTimeMs;
  }

  public void setQueueWaitTimeMs(final Integer queueWaitTimeMs) {
    this.queueWaitTimeMs = queueWaitTimeMs;
  }

  public Integer getApiCallCount() {
    return apiCallCount;
  }

  public void setApiCallCount(final Integer apiCallCount) {
    this.apiCallCount = apiCallCount;
  }

  public Integer getComplianceChecksPassed() {
    return complianceChecksPassed;
  }

  public void setComplianceChecksPassed(final Integer complianceChecksPassed) {
    this.complianceChecksPassed = complianceChecksPassed;
  }

  public Double getDataVolumeMb() {
    return dataVolumeMb;
  }

  public void setDataVolumeMb(final Double dataVolumeMb) {
    this.dataVolumeMb = dataVolumeMb;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(final Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public Double getCo2EmissionsKg() {
    return co2EmissionsKg;
  }

  public void setCo2EmissionsKg(final Double co2EmissionsKg) {
    this.co2EmissionsKg = co2EmissionsKg;
  }

  public Double getCustomerSatisfactionScore() {
    return customerSatisfactionScore;
  }

  public void setCustomerSatisfactionScore(final Double customerSatisfactionScore) {
    this.customerSatisfactionScore = customerSatisfactionScore;
  }

  public Double getFraudRiskScore() {
    return fraudRiskScore;
  }

  public void setFraudRiskScore(final Double fraudRiskScore) {
    this.fraudRiskScore = fraudRiskScore;
  }

  public Double getExternalServiceCostUsd() {
    return externalServiceCostUsd;
  }

  public void setExternalServiceCostUsd(final Double externalServiceCostUsd) {
    this.externalServiceCostUsd = externalServiceCostUsd;
  }

  public Boolean getSlaBreached() {
    return slaBreached;
  }

  public void setSlaBreached(final Boolean slaBreached) {
    this.slaBreached = slaBreached;
  }

  public Boolean getEscalated() {
    return escalated;
  }

  public void setEscalated(final Boolean escalated) {
    this.escalated = escalated;
  }

  public Boolean getManualOverride() {
    return manualOverride;
  }

  public void setManualOverride(final Boolean manualOverride) {
    this.manualOverride = manualOverride;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processInstanceKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ReportingMetricsDto that = (ReportingMetricsDto) o;
    return Objects.equals(processInstanceKey, that.processInstanceKey);
  }

  @Override
  public String toString() {
    return "ReportingMetricsDto(processInstanceKey=" + processInstanceKey + ")";
  }
}
