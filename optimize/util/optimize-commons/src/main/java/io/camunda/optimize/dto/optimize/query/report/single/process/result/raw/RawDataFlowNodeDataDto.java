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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RawDataFlowNodeDataDto that = (RawDataFlowNodeDataDto) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, startDate, endDate);
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
