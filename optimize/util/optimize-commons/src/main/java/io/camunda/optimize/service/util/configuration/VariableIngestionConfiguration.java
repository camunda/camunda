/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VariableIngestionConfiguration {

  @JsonProperty("maxBatchRequestBytes")
  private long maxBatchRequestBytes;

  @JsonProperty("maxRequests")
  private int maxRequests;

  public VariableIngestionConfiguration() {}

  public long getMaxBatchRequestBytes() {
    return maxBatchRequestBytes;
  }

  @JsonProperty("maxBatchRequestBytes")
  public void setMaxBatchRequestBytes(final long maxBatchRequestBytes) {
    this.maxBatchRequestBytes = maxBatchRequestBytes;
  }

  public int getMaxRequests() {
    return maxRequests;
  }

  @JsonProperty("maxRequests")
  public void setMaxRequests(final int maxRequests) {
    this.maxRequests = maxRequests;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableIngestionConfiguration;
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
    return "VariableIngestionConfiguration(maxBatchRequestBytes="
        + getMaxBatchRequestBytes()
        + ", maxRequests="
        + getMaxRequests()
        + ")";
  }
}
