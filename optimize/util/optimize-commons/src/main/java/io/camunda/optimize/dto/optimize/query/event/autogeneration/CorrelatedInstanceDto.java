/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import java.time.OffsetDateTime;

public class CorrelatedInstanceDto {

  private String sourceIdentifier;
  private OffsetDateTime startDate;

  public CorrelatedInstanceDto(final String sourceIdentifier, final OffsetDateTime startDate) {
    this.sourceIdentifier = sourceIdentifier;
    this.startDate = startDate;
  }

  public String getSourceIdentifier() {
    return sourceIdentifier;
  }

  public void setSourceIdentifier(final String sourceIdentifier) {
    this.sourceIdentifier = sourceIdentifier;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CorrelatedInstanceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $sourceIdentifier = getSourceIdentifier();
    result = result * PRIME + ($sourceIdentifier == null ? 43 : $sourceIdentifier.hashCode());
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CorrelatedInstanceDto)) {
      return false;
    }
    final CorrelatedInstanceDto other = (CorrelatedInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$sourceIdentifier = getSourceIdentifier();
    final Object other$sourceIdentifier = other.getSourceIdentifier();
    if (this$sourceIdentifier == null
        ? other$sourceIdentifier != null
        : !this$sourceIdentifier.equals(other$sourceIdentifier)) {
      return false;
    }
    final Object this$startDate = getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CorrelatedInstanceDto(sourceIdentifier="
        + getSourceIdentifier()
        + ", startDate="
        + getStartDate()
        + ")";
  }

  public static CorrelatedInstanceDtoBuilder builder() {
    return new CorrelatedInstanceDtoBuilder();
  }

  public static class CorrelatedInstanceDtoBuilder {

    private String sourceIdentifier;
    private OffsetDateTime startDate;

    CorrelatedInstanceDtoBuilder() {}

    public CorrelatedInstanceDtoBuilder sourceIdentifier(final String sourceIdentifier) {
      this.sourceIdentifier = sourceIdentifier;
      return this;
    }

    public CorrelatedInstanceDtoBuilder startDate(final OffsetDateTime startDate) {
      this.startDate = startDate;
      return this;
    }

    public CorrelatedInstanceDto build() {
      return new CorrelatedInstanceDto(sourceIdentifier, startDate);
    }

    @Override
    public String toString() {
      return "CorrelatedInstanceDto.CorrelatedInstanceDtoBuilder(sourceIdentifier="
          + sourceIdentifier
          + ", startDate="
          + startDate
          + ")";
    }
  }
}
