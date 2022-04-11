/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.telemetry;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@Builder
@Data
public class LicenseKeyDto {
  private final String customer;
  private final String type;
  @JsonProperty("valid-until")
  private final String validUntil;
  private final boolean unlimited;
  private final Map<String, String> features;
  private final String raw;
}
