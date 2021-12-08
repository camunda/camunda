/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

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

  public MixpanelHeartbeatProperties(final long processReportCount,
                                     final long decisionReportCount,
                                     final String stage,
                                     final String organizationId,
                                     final String clusterId) {
    super(stage, organizationId, clusterId);
    this.processReportCount = processReportCount;
    this.decisionReportCount = decisionReportCount;
  }
}
