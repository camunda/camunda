/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.core;

import io.atomix.cluster.MemberId;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * A GLOBAL value as one member holds it, with what is needed to merge it with another member's
 * copy. For last-writer-wins keys the timestamp and writer order writes; for custom-merge keys they
 * are unused.
 */
public record GlobalEntry(
    byte[] bytes, boolean lastWriterWins, long timestamp, @Nullable MemberId writer) {

  public static GlobalEntry lastWriterWins(
      final byte[] bytes, final long timestamp, final MemberId writer) {
    return new GlobalEntry(bytes, true, timestamp, writer);
  }

  public static GlobalEntry merged(final byte[] bytes) {
    return new GlobalEntry(bytes, false, 0, null);
  }

  /** Whether this last-writer-wins write wins over {@code other}. */
  public boolean isNewerThan(final GlobalEntry other) {
    if (timestamp != other.timestamp) {
      return timestamp > other.timestamp;
    }
    if (writer == null || other.writer == null) {
      return false;
    }
    return MemberId.ID_COMPARATOR.compare(writer, other.writer) > 0;
  }

  public boolean hasSameBytes(final @Nullable GlobalEntry other) {
    return other != null && Arrays.equals(bytes, other.bytes);
  }
}
