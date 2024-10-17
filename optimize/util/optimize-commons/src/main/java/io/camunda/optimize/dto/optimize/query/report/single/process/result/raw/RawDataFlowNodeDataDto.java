/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;

public class RawDataFlowNodeDataDto implements RawDataInstanceDto {

  private String id;
  private String name;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  public RawDataFlowNodeDataDto(
      final String id,
      final String name,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate) {
    this.id = id;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public RawDataFlowNodeDataDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof RawDataFlowNodeDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    final Object $endDate = getEndDate();
    result = result * PRIME + ($endDate == null ? 43 : $endDate.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RawDataFlowNodeDataDto)) {
      return false;
    }
    final RawDataFlowNodeDataDto other = (RawDataFlowNodeDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$startDate = getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    final Object this$endDate = getEndDate();
    final Object other$endDate = other.getEndDate();
    if (this$endDate == null ? other$endDate != null : !this$endDate.equals(other$endDate)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "RawDataFlowNodeDataDto(id="
        + getId()
        + ", name="
        + getName()
        + ", startDate="
        + getStartDate()
        + ", endDate="
        + getEndDate()
        + ")";
  }
}
