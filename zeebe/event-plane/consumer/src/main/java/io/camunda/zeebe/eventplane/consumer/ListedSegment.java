/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.SegmentName;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

/** A segment as a listing shows it: its key, its size, and what its name says. */
record ListedSegment(String key, long size, SegmentName name) {

  /**
   * Of segments that hold an entry, a reader prefers the highest level, then the highest last
   * index, then the earlier ULID, which is the rollup that maintenance keeps of two.
   */
  static final Comparator<ListedSegment> PREFERENCE =
      Comparator.<ListedSegment>comparingInt(segment -> segment.name().level())
          .thenComparingLong(ListedSegment::lastIndex)
          .thenComparing(segment -> segment.name().ulid(), Comparator.reverseOrder());

  /** The segment, or null if the key isn't a segment's. */
  static @Nullable ListedSegment of(final StoredObject object) {
    try {
      return new ListedSegment(object.key(), object.size(), ObjectNames.parseSegment(object.key()));
    } catch (final InvalidObjectNameException e) {
      return null;
    }
  }

  /** The one of two segments a reader prefers. */
  static ListedSegment better(@Nullable final ListedSegment a, final ListedSegment b) {
    return a == null || PREFERENCE.compare(b, a) > 0 ? b : a;
  }

  long firstIndex() {
    return name.firstIndex();
  }

  long lastIndex() {
    return name.lastIndex();
  }
}
