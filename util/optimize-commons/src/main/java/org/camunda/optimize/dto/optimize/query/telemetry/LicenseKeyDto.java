/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.telemetry;

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
  private final String validUntil;
  private final Boolean isUnlimited;
  private final Map<String, String> features;
}
