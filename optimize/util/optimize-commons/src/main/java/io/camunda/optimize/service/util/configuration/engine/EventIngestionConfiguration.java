/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class EventIngestionConfiguration {

  @JsonProperty("maxBatchRequestBytes")
  private long maxBatchRequestBytes;

  @JsonProperty("maxRequests")
  private int maxRequests;

  public EventIngestionConfiguration() {}

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
    return other instanceof EventIngestionConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventIngestionConfiguration that = (EventIngestionConfiguration) o;
    return maxBatchRequestBytes == that.maxBatchRequestBytes && maxRequests == that.maxRequests;
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxBatchRequestBytes, maxRequests);
  }

  @Override
  public String toString() {
    return "EventIngestionConfiguration(maxBatchRequestBytes="
        + getMaxBatchRequestBytes()
        + ", maxRequests="
        + getMaxRequests()
        + ")";
  }
}
