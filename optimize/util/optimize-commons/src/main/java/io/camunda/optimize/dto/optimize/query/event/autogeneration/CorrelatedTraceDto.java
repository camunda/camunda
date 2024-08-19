/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import java.util.List;

public class CorrelatedTraceDto {

  private String correlationValue;
  private List<CorrelatedInstanceDto> instances;

  public CorrelatedTraceDto(
      final String correlationValue, final List<CorrelatedInstanceDto> instances) {
    this.correlationValue = correlationValue;
    this.instances = instances;
  }

  public String getCorrelationValue() {
    return correlationValue;
  }

  public void setCorrelationValue(final String correlationValue) {
    this.correlationValue = correlationValue;
  }

  public List<CorrelatedInstanceDto> getInstances() {
    return instances;
  }

  public void setInstances(final List<CorrelatedInstanceDto> instances) {
    this.instances = instances;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CorrelatedTraceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $correlationValue = getCorrelationValue();
    result = result * PRIME + ($correlationValue == null ? 43 : $correlationValue.hashCode());
    final Object $instances = getInstances();
    result = result * PRIME + ($instances == null ? 43 : $instances.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CorrelatedTraceDto)) {
      return false;
    }
    final CorrelatedTraceDto other = (CorrelatedTraceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$correlationValue = getCorrelationValue();
    final Object other$correlationValue = other.getCorrelationValue();
    if (this$correlationValue == null
        ? other$correlationValue != null
        : !this$correlationValue.equals(other$correlationValue)) {
      return false;
    }
    final Object this$instances = getInstances();
    final Object other$instances = other.getInstances();
    if (this$instances == null
        ? other$instances != null
        : !this$instances.equals(other$instances)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CorrelatedTraceDto(correlationValue="
        + getCorrelationValue()
        + ", instances="
        + getInstances()
        + ")";
  }

  public static CorrelatedTraceDtoBuilder builder() {
    return new CorrelatedTraceDtoBuilder();
  }

  public static class CorrelatedTraceDtoBuilder {

    private String correlationValue;
    private List<CorrelatedInstanceDto> instances;

    CorrelatedTraceDtoBuilder() {}

    public CorrelatedTraceDtoBuilder correlationValue(final String correlationValue) {
      this.correlationValue = correlationValue;
      return this;
    }

    public CorrelatedTraceDtoBuilder instances(final List<CorrelatedInstanceDto> instances) {
      this.instances = instances;
      return this;
    }

    public CorrelatedTraceDto build() {
      return new CorrelatedTraceDto(correlationValue, instances);
    }

    @Override
    public String toString() {
      return "CorrelatedTraceDto.CorrelatedTraceDtoBuilder(correlationValue="
          + correlationValue
          + ", instances="
          + instances
          + ")";
    }
  }
}
