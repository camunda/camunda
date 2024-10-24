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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
