/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class UpgradeStepLogEntryDto {

  @NonNull private String indexName;
  @NonNull private String optimizeVersion;
  @NonNull private UpgradeStepType stepType;
  @NonNull private Integer stepNumber;
  private Instant appliedDate;

  public UpgradeStepLogEntryDto(
      @NonNull String indexName,
      @NonNull String optimizeVersion,
      @NonNull UpgradeStepType stepType,
      @NonNull Integer stepNumber,
      Instant appliedDate) {
    this.indexName = indexName;
    this.optimizeVersion = optimizeVersion;
    this.stepType = stepType;
    this.stepNumber = stepNumber;
    this.appliedDate = appliedDate;
  }

  protected UpgradeStepLogEntryDto() {}

  @JsonIgnore
  public String getId() {
    return String.join("_", optimizeVersion, stepType.toString(), indexName);
  }

  public static final class Fields {

    public static final String indexName = "indexName";
    public static final String optimizeVersion = "optimizeVersion";
    public static final String stepType = "stepType";
    public static final String stepNumber = "stepNumber";
    public static final String appliedDate = "appliedDate";
  }
}
