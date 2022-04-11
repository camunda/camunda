/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@Builder
@FieldNameConstants
public class UpgradeStepLogEntryDto {
  @NonNull
  private String indexName;
  @NonNull
  private String optimizeVersion;
  @NonNull
  private UpgradeStepType stepType;
  @NonNull
  private Integer stepNumber;
  private Instant appliedDate;

  @JsonIgnore
  public String getId() {
    return String.join("_", optimizeVersion, stepType.toString(), indexName);
  }
}
