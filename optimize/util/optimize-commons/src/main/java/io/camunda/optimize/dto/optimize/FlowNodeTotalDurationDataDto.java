/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

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
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "FlowNodeTotalDurationDataDto(name=" + getName() + ", value=" + getValue() + ")";
  }
}
