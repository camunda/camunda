/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
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
}
