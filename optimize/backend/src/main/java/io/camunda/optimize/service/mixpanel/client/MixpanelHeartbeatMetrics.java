/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MixpanelHeartbeatMetrics {

  private long processReportCount;
  private long decisionReportCount;
  private long dashboardCount;
  private long reportShareCount;
  private long dashboardShareCount;
  private long alertCount;
  private long taskReportCount;
}
