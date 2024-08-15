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

public class UpgradeStepLogEntryDto {

  private String indexName;
  private String optimizeVersion;
  private UpgradeStepType stepType;
  private Integer stepNumber;
  private Instant appliedDate;

  public UpgradeStepLogEntryDto(
      final String indexName,
      final String optimizeVersion,
      final UpgradeStepType stepType,
      final Integer stepNumber,
      final Instant appliedDate) {
    if (indexName == null) {
      throw new IllegalArgumentException("indexName cannot be null");
    }

    if (optimizeVersion == null) {
      throw new IllegalArgumentException("optimizeVersion cannot be null");
    }

    if (stepType == null) {
      throw new IllegalArgumentException("stepType cannot be null");
    }

    if (stepNumber == null) {
      throw new IllegalArgumentException("stepNumber cannot be null");
    }

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

  public String getIndexName() {
    return indexName;
  }

  public void setIndexName(final String indexName) {
    if (indexName == null) {
      throw new IllegalArgumentException("indexName cannot be null");
    }

    this.indexName = indexName;
  }

  public String getOptimizeVersion() {
    return optimizeVersion;
  }

  public void setOptimizeVersion(final String optimizeVersion) {
    if (optimizeVersion == null) {
      throw new IllegalArgumentException("optimizeVersion cannot be null");
    }

    this.optimizeVersion = optimizeVersion;
  }

  public UpgradeStepType getStepType() {
    return stepType;
  }

  public void setStepType(final UpgradeStepType stepType) {
    if (stepType == null) {
      throw new IllegalArgumentException("stepType cannot be null");
    }

    this.stepType = stepType;
  }

  public Integer getStepNumber() {
    return stepNumber;
  }

  public void setStepNumber(final Integer stepNumber) {
    if (stepNumber == null) {
      throw new IllegalArgumentException("stepNumber cannot be null");
    }

    this.stepNumber = stepNumber;
  }

  public Instant getAppliedDate() {
    return appliedDate;
  }

  public void setAppliedDate(final Instant appliedDate) {
    this.appliedDate = appliedDate;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UpgradeStepLogEntryDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $indexName = getIndexName();
    result = result * PRIME + ($indexName == null ? 43 : $indexName.hashCode());
    final Object $optimizeVersion = getOptimizeVersion();
    result = result * PRIME + ($optimizeVersion == null ? 43 : $optimizeVersion.hashCode());
    final Object $stepType = getStepType();
    result = result * PRIME + ($stepType == null ? 43 : $stepType.hashCode());
    final Object $stepNumber = getStepNumber();
    result = result * PRIME + ($stepNumber == null ? 43 : $stepNumber.hashCode());
    final Object $appliedDate = getAppliedDate();
    result = result * PRIME + ($appliedDate == null ? 43 : $appliedDate.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UpgradeStepLogEntryDto)) {
      return false;
    }
    final UpgradeStepLogEntryDto other = (UpgradeStepLogEntryDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$indexName = getIndexName();
    final Object other$indexName = other.getIndexName();
    if (this$indexName == null
        ? other$indexName != null
        : !this$indexName.equals(other$indexName)) {
      return false;
    }
    final Object this$optimizeVersion = getOptimizeVersion();
    final Object other$optimizeVersion = other.getOptimizeVersion();
    if (this$optimizeVersion == null
        ? other$optimizeVersion != null
        : !this$optimizeVersion.equals(other$optimizeVersion)) {
      return false;
    }
    final Object this$stepType = getStepType();
    final Object other$stepType = other.getStepType();
    if (this$stepType == null ? other$stepType != null : !this$stepType.equals(other$stepType)) {
      return false;
    }
    final Object this$stepNumber = getStepNumber();
    final Object other$stepNumber = other.getStepNumber();
    if (this$stepNumber == null
        ? other$stepNumber != null
        : !this$stepNumber.equals(other$stepNumber)) {
      return false;
    }
    final Object this$appliedDate = getAppliedDate();
    final Object other$appliedDate = other.getAppliedDate();
    if (this$appliedDate == null
        ? other$appliedDate != null
        : !this$appliedDate.equals(other$appliedDate)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UpgradeStepLogEntryDto(indexName="
        + getIndexName()
        + ", optimizeVersion="
        + getOptimizeVersion()
        + ", stepType="
        + getStepType()
        + ", stepNumber="
        + getStepNumber()
        + ", appliedDate="
        + getAppliedDate()
        + ")";
  }

  public static UpgradeStepLogEntryDtoBuilder builder() {
    return new UpgradeStepLogEntryDtoBuilder();
  }

  public static final class Fields {

    public static final String indexName = "indexName";
    public static final String optimizeVersion = "optimizeVersion";
    public static final String stepType = "stepType";
    public static final String stepNumber = "stepNumber";
    public static final String appliedDate = "appliedDate";
  }

  public static class UpgradeStepLogEntryDtoBuilder {

    private String indexName;
    private String optimizeVersion;
    private UpgradeStepType stepType;
    private Integer stepNumber;
    private Instant appliedDate;

    UpgradeStepLogEntryDtoBuilder() {}

    public UpgradeStepLogEntryDtoBuilder indexName(final String indexName) {
      if (indexName == null) {
        throw new IllegalArgumentException("indexName cannot be null");
      }

      this.indexName = indexName;
      return this;
    }

    public UpgradeStepLogEntryDtoBuilder optimizeVersion(final String optimizeVersion) {
      if (optimizeVersion == null) {
        throw new IllegalArgumentException("optimizeVersion cannot be null");
      }

      this.optimizeVersion = optimizeVersion;
      return this;
    }

    public UpgradeStepLogEntryDtoBuilder stepType(final UpgradeStepType stepType) {
      if (stepType == null) {
        throw new IllegalArgumentException("stepType cannot be null");
      }

      this.stepType = stepType;
      return this;
    }

    public UpgradeStepLogEntryDtoBuilder stepNumber(final Integer stepNumber) {
      if (stepNumber == null) {
        throw new IllegalArgumentException("stepNumber cannot be null");
      }

      this.stepNumber = stepNumber;
      return this;
    }

    public UpgradeStepLogEntryDtoBuilder appliedDate(final Instant appliedDate) {
      this.appliedDate = appliedDate;
      return this;
    }

    public UpgradeStepLogEntryDto build() {
      return new UpgradeStepLogEntryDto(
          indexName, optimizeVersion, stepType, stepNumber, appliedDate);
    }

    @Override
    public String toString() {
      return "UpgradeStepLogEntryDto.UpgradeStepLogEntryDtoBuilder(indexName="
          + indexName
          + ", optimizeVersion="
          + optimizeVersion
          + ", stepType="
          + stepType
          + ", stepNumber="
          + stepNumber
          + ", appliedDate="
          + appliedDate
          + ")";
    }
  }
}
