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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
