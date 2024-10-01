/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $index = getIndex();
    result = result * PRIME + ($index == null ? 43 : $index.hashCode());
    final Object $aggregationBucketLimit = getAggregationBucketLimit();
    result =
        result * PRIME
            + ($aggregationBucketLimit == null ? 43 : $aggregationBucketLimit.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DatabaseSettings)) {
      return false;
    }
    final DatabaseSettings other = (DatabaseSettings) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$index = getIndex();
    final Object other$index = other.getIndex();
    if (this$index == null ? other$index != null : !this$index.equals(other$index)) {
      return false;
    }
    final Object this$aggregationBucketLimit = getAggregationBucketLimit();
    final Object other$aggregationBucketLimit = other.getAggregationBucketLimit();
    if (this$aggregationBucketLimit == null
        ? other$aggregationBucketLimit != null
        : !this$aggregationBucketLimit.equals(other$aggregationBucketLimit)) {
      return false;
    }
    return true;
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
