/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import java.time.OffsetDateTime;

public abstract class CorrelatableInstanceDto {

  private OffsetDateTime startDate;

  public CorrelatableInstanceDto(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public CorrelatableInstanceDto() {}

  public abstract String getSourceIdentifier();

  public abstract String getCorrelationValueForEventSource(
      EventSourceEntryDto<?> eventSourceEntryDto);

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CorrelatableInstanceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CorrelatableInstanceDto)) {
      return false;
    }
    final CorrelatableInstanceDto other = (CorrelatableInstanceDto) o;
    if (!other.canEqual((Object) this)) {
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
    return "CorrelatableInstanceDto(startDate=" + getStartDate() + ")";
  }
}
