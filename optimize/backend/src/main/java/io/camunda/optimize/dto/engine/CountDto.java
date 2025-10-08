/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import java.util.Objects;

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
    return Objects.hash(count);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CountDto that = (CountDto) o;
    return count == that.count;
  }

  @Override
  public String toString() {
    return "CountDto(count=" + getCount() + ")";
  }
}
