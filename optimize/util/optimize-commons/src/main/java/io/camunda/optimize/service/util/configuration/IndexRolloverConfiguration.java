/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class IndexRolloverConfiguration {

  private int scheduleIntervalInMinutes;
  private int maxIndexSizeGB;

  public IndexRolloverConfiguration(final int scheduleIntervalInMinutes, final int maxIndexSizeGB) {
    this.scheduleIntervalInMinutes = scheduleIntervalInMinutes;
    this.maxIndexSizeGB = maxIndexSizeGB;
  }

  protected IndexRolloverConfiguration() {}

  public int getScheduleIntervalInMinutes() {
    return scheduleIntervalInMinutes;
  }

  public void setScheduleIntervalInMinutes(final int scheduleIntervalInMinutes) {
    this.scheduleIntervalInMinutes = scheduleIntervalInMinutes;
  }

  public int getMaxIndexSizeGB() {
    return maxIndexSizeGB;
  }

  public void setMaxIndexSizeGB(final int maxIndexSizeGB) {
    this.maxIndexSizeGB = maxIndexSizeGB;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IndexRolloverConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getScheduleIntervalInMinutes();
    result = result * PRIME + getMaxIndexSizeGB();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IndexRolloverConfiguration)) {
      return false;
    }
    final IndexRolloverConfiguration other = (IndexRolloverConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getScheduleIntervalInMinutes() != other.getScheduleIntervalInMinutes()) {
      return false;
    }
    if (getMaxIndexSizeGB() != other.getMaxIndexSizeGB()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "IndexRolloverConfiguration(scheduleIntervalInMinutes="
        + getScheduleIntervalInMinutes()
        + ", maxIndexSizeGB="
        + getMaxIndexSizeGB()
        + ")";
  }
}
