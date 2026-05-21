/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.nio.ByteBuffer;

/** Persisted state for the analytics exporter, stored alongside the last exported position. */
final class AnalyticsExporterMetadata {

  private long sequenceNumber;

  AnalyticsExporterMetadata(final long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  long getSequenceNumber() {
    return sequenceNumber;
  }

  void incrementSequenceNumber() {
    sequenceNumber++;
  }

  byte[] serialize() {
    return ByteBuffer.allocate(Long.BYTES).putLong(sequenceNumber).array();
  }

  static AnalyticsExporterMetadata deserialize(final byte[] bytes) {
    return new AnalyticsExporterMetadata(ByteBuffer.wrap(bytes).getLong());
  }
}
