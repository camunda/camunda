/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

public class ZeebeConfigDto implements SchedulerConfig {

  private String name;
  private int partitionCount;

  public ZeebeConfigDto(final String name, final int partitionCount) {
    this.name = name;
    this.partitionCount = partitionCount;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public void setPartitionCount(final int partitionCount) {
    this.partitionCount = partitionCount;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeConfigDto;
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
    return "ZeebeConfigDto(name=" + getName() + ", partitionCount=" + getPartitionCount() + ")";
  }
}
