/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

public class CountDto {

  protected long count;

  public CountDto() {}

  public long getCount() {
    return count;
  }

  public void setCount(final long count) {
    this.count = count;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CountDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $count = getCount();
    result = result * PRIME + (int) ($count >>> 32 ^ $count);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CountDto)) {
      return false;
    }
    final CountDto other = (CountDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getCount() != other.getCount()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CountDto(count=" + getCount() + ")";
  }
}
