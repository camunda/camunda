/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.store.SegmentName;
import java.time.Instant;

/**
 * A segment in the store, as a listing shows it.
 *
 * @param key the object's key
 * @param name what the key says
 * @param size the object's size in bytes
 */
record StoredSegment(String key, SegmentName name, long size) {

  /** The level of a publisher's segment. */
  static final int PUBLISHER_LEVEL = 0;

  /** The level of a rollup. */
  static final int ROLLUP_LEVEL = 1;

  long firstIndex() {
    return name.firstIndex();
  }

  long lastIndex() {
    return name.lastIndex();
  }

  /** When it was uploaded, which is the time in its ULID. */
  Instant uploadedAt() {
    return UlidTime.of(name.ulid());
  }

  /** Whether it was uploaded before the instant. */
  boolean isUploadedBefore(final Instant instant) {
    return uploadedAt().isBefore(instant);
  }

  /** Whether the other segment's range of indexes lies within this one's, or is the same. */
  boolean covers(final StoredSegment other) {
    return firstIndex() <= other.firstIndex() && other.lastIndex() <= lastIndex();
  }

  /** Whether its ULID, and so its upload, comes before the other's. */
  boolean isUploadedBeforeThan(final StoredSegment other) {
    return name.ulid().compareTo(other.name.ulid()) < 0;
  }
}
