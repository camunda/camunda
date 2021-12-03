/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.telemetry.TelemetryDataConstants;

import java.util.UUID;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MixpanelEventProperties {
  // Mixpanel default properties, see https://developer.mixpanel.com/reference/import-events#high-level-requirements
  @JsonProperty("time")
  private long time = System.currentTimeMillis();
  // defaults to empty string which equals "no user association"
  // see https://developer.mixpanel.com/reference/import-events#propertiesdistinct_id
  @JsonProperty("distinct_id")
  private String distinctId = "";
  @JsonProperty("$insert_id")
  private String insertId = UUID.randomUUID().toString();

  // Custom properties
  @JsonProperty("product")
  private String product = TelemetryDataConstants.OPTIMIZE_PRODUCT;
  @JsonProperty("organization")
  private String organizationId;

  public MixpanelEventProperties(final String organizationId) {
    this.organizationId = organizationId;
  }
}
