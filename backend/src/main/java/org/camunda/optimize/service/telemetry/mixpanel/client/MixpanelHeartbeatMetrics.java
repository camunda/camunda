/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

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

}
