/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Persisted state for the analytics exporter, stored alongside the last exported position. */
@JsonIgnoreProperties(ignoreUnknown = true)
final class AnalyticsExporterMetadata {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private long eventSequenceNumber;

  private long metricSequenceNumber;

  /**
   * Transient flag: true when a mutating method has been called since the last {@link #serialize()}
   * or construction. Never persisted — Jackson setters must NOT set this flag so that a freshly
   * deserialized object starts clean.
   */
  @JsonIgnore private boolean dirty;

  /** No-arg constructor required by Jackson. */
  AnalyticsExporterMetadata() {}

  AnalyticsExporterMetadata(final long eventSequenceNumber, final long metricSequenceNumber) {
    this.eventSequenceNumber = eventSequenceNumber;
    this.metricSequenceNumber = metricSequenceNumber;
  }

  public long getEventSequenceNumber() {
    return eventSequenceNumber;
  }

  public void setEventSequenceNumber(final long eventSequenceNumber) {
    this.eventSequenceNumber = eventSequenceNumber;
  }

  public long incrementAndGetEventSequenceNumber() {
    dirty = true;
    return ++eventSequenceNumber;
  }

  public long getMetricSequenceNumber() {
    return metricSequenceNumber;
  }

  public void setMetricSequenceNumber(final long metricSequenceNumber) {
    this.metricSequenceNumber = metricSequenceNumber;
  }

  public long incrementAndGetMetricSequenceNumber() {
    dirty = true;
    return ++metricSequenceNumber;
  }

  /** Returns true if a mutating method has been called since construction or last serialization. */
  @JsonIgnore
  boolean isDirty() {
    return dirty;
  }

  byte[] serialize() {
    try {
      final var bytes = OBJECT_MAPPER.writeValueAsBytes(this);
      dirty = false;
      return bytes;
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
