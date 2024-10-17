/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MixpanelHeartbeatProperties extends MixpanelEventProperties {

  @JsonProperty("processReportCount")
  private long processReportCount;

  @JsonProperty("decisionReportCount")
  private long decisionReportCount;

  @JsonProperty("dashboardCount")
  private long dashboardCount;

  @JsonProperty("reportShareCount")
  private long reportShareCount;

  @JsonProperty("dashboardShareCount")
  private long dashboardShareCount;

  @JsonProperty("alertCount")
  private long alertCount;

  @JsonProperty("taskReportCount")
  private long taskReportCount;

  public MixpanelHeartbeatProperties(
      final MixpanelHeartbeatMetrics mixpanelHeartbeatMetrics,
      final String stage,
      final String organizationId,
      final String clusterId) {
    super(stage, organizationId, clusterId);
    processReportCount = mixpanelHeartbeatMetrics.getProcessReportCount();
    decisionReportCount = mixpanelHeartbeatMetrics.getDecisionReportCount();
    dashboardCount = mixpanelHeartbeatMetrics.getDashboardCount();
    reportShareCount = mixpanelHeartbeatMetrics.getReportShareCount();
    dashboardShareCount = mixpanelHeartbeatMetrics.getDashboardShareCount();
    alertCount = mixpanelHeartbeatMetrics.getAlertCount();
    taskReportCount = mixpanelHeartbeatMetrics.getTaskReportCount();
  }

  public long getProcessReportCount() {
    return processReportCount;
  }

  @JsonProperty("processReportCount")
  public void setProcessReportCount(final long processReportCount) {
    this.processReportCount = processReportCount;
  }

  public long getDecisionReportCount() {
    return decisionReportCount;
  }

  @JsonProperty("decisionReportCount")
  public void setDecisionReportCount(final long decisionReportCount) {
    this.decisionReportCount = decisionReportCount;
  }

  public long getDashboardCount() {
    return dashboardCount;
  }

  @JsonProperty("dashboardCount")
  public void setDashboardCount(final long dashboardCount) {
    this.dashboardCount = dashboardCount;
  }

  public long getReportShareCount() {
    return reportShareCount;
  }

  @JsonProperty("reportShareCount")
  public void setReportShareCount(final long reportShareCount) {
    this.reportShareCount = reportShareCount;
  }

  public long getDashboardShareCount() {
    return dashboardShareCount;
  }

  @JsonProperty("dashboardShareCount")
  public void setDashboardShareCount(final long dashboardShareCount) {
    this.dashboardShareCount = dashboardShareCount;
  }

  public long getAlertCount() {
    return alertCount;
  }

  @JsonProperty("alertCount")
  public void setAlertCount(final long alertCount) {
    this.alertCount = alertCount;
  }

  public long getTaskReportCount() {
    return taskReportCount;
  }

  @JsonProperty("taskReportCount")
  public void setTaskReportCount(final long taskReportCount) {
    this.taskReportCount = taskReportCount;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelHeartbeatProperties;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "MixpanelHeartbeatProperties(processReportCount="
        + getProcessReportCount()
        + ", decisionReportCount="
        + getDecisionReportCount()
        + ", dashboardCount="
        + getDashboardCount()
        + ", reportShareCount="
        + getReportShareCount()
        + ", dashboardShareCount="
        + getDashboardShareCount()
        + ", alertCount="
        + getAlertCount()
        + ", taskReportCount="
        + getTaskReportCount()
        + ")";
  }
}
