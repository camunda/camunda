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
    final int PRIME = 59;
    int result = super.hashCode();
    final long $processReportCount = getProcessReportCount();
    result = result * PRIME + (int) ($processReportCount >>> 32 ^ $processReportCount);
    final long $decisionReportCount = getDecisionReportCount();
    result = result * PRIME + (int) ($decisionReportCount >>> 32 ^ $decisionReportCount);
    final long $dashboardCount = getDashboardCount();
    result = result * PRIME + (int) ($dashboardCount >>> 32 ^ $dashboardCount);
    final long $reportShareCount = getReportShareCount();
    result = result * PRIME + (int) ($reportShareCount >>> 32 ^ $reportShareCount);
    final long $dashboardShareCount = getDashboardShareCount();
    result = result * PRIME + (int) ($dashboardShareCount >>> 32 ^ $dashboardShareCount);
    final long $alertCount = getAlertCount();
    result = result * PRIME + (int) ($alertCount >>> 32 ^ $alertCount);
    final long $taskReportCount = getTaskReportCount();
    result = result * PRIME + (int) ($taskReportCount >>> 32 ^ $taskReportCount);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MixpanelHeartbeatProperties)) {
      return false;
    }
    final MixpanelHeartbeatProperties other = (MixpanelHeartbeatProperties) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (getProcessReportCount() != other.getProcessReportCount()) {
      return false;
    }
    if (getDecisionReportCount() != other.getDecisionReportCount()) {
      return false;
    }
    if (getDashboardCount() != other.getDashboardCount()) {
      return false;
    }
    if (getReportShareCount() != other.getReportShareCount()) {
      return false;
    }
    if (getDashboardShareCount() != other.getDashboardShareCount()) {
      return false;
    }
    if (getAlertCount() != other.getAlertCount()) {
      return false;
    }
    if (getTaskReportCount() != other.getTaskReportCount()) {
      return false;
    }
    return true;
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
