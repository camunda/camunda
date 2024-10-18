/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import io.camunda.optimize.dto.optimize.OptimizeDto;

public class AggregationDto implements OptimizeDto {

  AggregationType type;
  Double value;

  public AggregationDto(final AggregationType aggregationType) {
    type = aggregationType;
  }

  public AggregationDto(final AggregationType type, final Double value) {
    this.type = type;
    this.value = value;
  }

  public AggregationDto() {}

  public AggregationType getType() {
    return type;
  }

  public void setType(final AggregationType type) {
    this.type = type;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(final Double value) {
    this.value = value;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AggregationDto;
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
    return "AggregationDto(type=" + getType() + ", value=" + getValue() + ")";
  }
}
