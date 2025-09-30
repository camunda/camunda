/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import java.util.Objects;

public class DatabaseSettings {

  private DatabaseIndex index;
  private Integer aggregationBucketLimit;

  public DatabaseSettings() {}

  public DatabaseIndex getIndex() {
    return index;
  }

  public void setIndex(final DatabaseIndex index) {
    this.index = index;
  }

  public Integer getAggregationBucketLimit() {
    return aggregationBucketLimit;
  }

  public void setAggregationBucketLimit(final Integer aggregationBucketLimit) {
    this.aggregationBucketLimit = aggregationBucketLimit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseSettings;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DatabaseSettings that = (DatabaseSettings) o;
    return Objects.equals(index, that.index)
        && Objects.equals(aggregationBucketLimit, that.aggregationBucketLimit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, aggregationBucketLimit);
  }

  @Override
  public String toString() {
    return "DatabaseSettings(index="
        + getIndex()
        + ", aggregationBucketLimit="
        + getAggregationBucketLimit()
        + ")";
  }
}
