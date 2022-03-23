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
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@Builder
@Data
public class InternalsDto implements OptimizeDto {
  @NonNull
  @Builder.Default
  @JsonProperty("engine-installation-ids")
  private List<String> engineInstallationIds = new ArrayList<>();

  @NonNull
  private final DatabaseDto database;

  @JsonProperty("license-key")
  private final LicenseKeyDto licenseKey;
}
