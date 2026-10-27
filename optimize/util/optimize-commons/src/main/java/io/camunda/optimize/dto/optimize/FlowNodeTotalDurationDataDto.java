/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.util.Objects;

public class FlowNodeTotalDurationDataDto {

  String name;
  long value;

  public FlowNodeTotalDurationDataDto(final String name, final long value) {
    this.name = name;
    this.value = value;
  }

  public FlowNodeTotalDurationDataDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public long getValue() {
    return value;
  }

  public void setValue(final long value) {
    this.value = value;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeTotalDurationDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeTotalDurationDataDto that = (FlowNodeTotalDurationDataDto) o;
    return value == that.value && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  @Override
  public String toString() {
    return "FlowNodeTotalDurationDataDto(name=" + getName() + ", value=" + getValue() + ")";
  }
}
