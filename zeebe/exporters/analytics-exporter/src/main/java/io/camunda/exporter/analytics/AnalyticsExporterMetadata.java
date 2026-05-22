/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Persisted state for the analytics exporter, stored alongside the last exported position. */
@JsonIgnoreProperties(ignoreUnknown = true)
final class AnalyticsExporterMetadata {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private long rawEventSequenceNumber;

  /** No-arg constructor required by Jackson. */
  AnalyticsExporterMetadata() {}

  AnalyticsExporterMetadata(final long rawEventSequenceNumber) {
    this.rawEventSequenceNumber = rawEventSequenceNumber;
  }

  public long getRawEventSequenceNumber() {
    return rawEventSequenceNumber;
  }

  public void setRawEventSequenceNumber(final long rawEventSequenceNumber) {
    this.rawEventSequenceNumber = rawEventSequenceNumber;
  }

  public long incrementAndGetRawEventSequenceNumber() {
    return ++rawEventSequenceNumber;
  }

  byte[] serialize() {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(this);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize exporter metadata", e);
    }
  }

  static AnalyticsExporterMetadata deserialize(final byte[] bytes) {
    try {
      return OBJECT_MAPPER.readValue(bytes, AnalyticsExporterMetadata.class);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to deserialize exporter metadata", e);
    }
  }
}
