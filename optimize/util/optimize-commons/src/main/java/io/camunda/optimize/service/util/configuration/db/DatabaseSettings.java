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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
