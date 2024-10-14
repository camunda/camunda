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
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AggregationDto)) {
      return false;
    }
    final AggregationDto other = (AggregationDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AggregationDto(type=" + getType() + ", value=" + getValue() + ")";
  }
}
