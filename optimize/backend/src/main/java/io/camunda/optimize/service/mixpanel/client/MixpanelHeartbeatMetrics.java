/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

public class MixpanelHeartbeatMetrics {

  private long processReportCount;
  private long decisionReportCount;
  private long dashboardCount;
  private long reportShareCount;
  private long dashboardShareCount;
  private long alertCount;
  private long taskReportCount;

  public MixpanelHeartbeatMetrics(
      final long processReportCount,
      final long decisionReportCount,
      final long dashboardCount,
      final long reportShareCount,
      final long dashboardShareCount,
      final long alertCount,
      final long taskReportCount) {
    this.processReportCount = processReportCount;
    this.decisionReportCount = decisionReportCount;
    this.dashboardCount = dashboardCount;
    this.reportShareCount = reportShareCount;
    this.dashboardShareCount = dashboardShareCount;
    this.alertCount = alertCount;
    this.taskReportCount = taskReportCount;
  }

  public long getProcessReportCount() {
    return processReportCount;
  }

  public void setProcessReportCount(final long processReportCount) {
    this.processReportCount = processReportCount;
  }

  public long getDecisionReportCount() {
    return decisionReportCount;
  }

  public void setDecisionReportCount(final long decisionReportCount) {
    this.decisionReportCount = decisionReportCount;
  }

  public long getDashboardCount() {
    return dashboardCount;
  }

  public void setDashboardCount(final long dashboardCount) {
    this.dashboardCount = dashboardCount;
  }

  public long getReportShareCount() {
    return reportShareCount;
  }

  public void setReportShareCount(final long reportShareCount) {
    this.reportShareCount = reportShareCount;
  }

  public long getDashboardShareCount() {
    return dashboardShareCount;
  }

  public void setDashboardShareCount(final long dashboardShareCount) {
    this.dashboardShareCount = dashboardShareCount;
  }

  public long getAlertCount() {
    return alertCount;
  }

  public void setAlertCount(final long alertCount) {
    this.alertCount = alertCount;
  }

  public long getTaskReportCount() {
    return taskReportCount;
  }

  public void setTaskReportCount(final long taskReportCount) {
    this.taskReportCount = taskReportCount;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelHeartbeatMetrics;
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
    return "MixpanelHeartbeatMetrics(processReportCount="
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
