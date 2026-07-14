/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.serving.spi.WriteVersion;

/**
 * Packs a {@link WriteVersion} into the single non-negative {@code long} the document stores'
 * external versioning compares: the epoch in bits 48–62, the offset in bits 0–47. The packing is
 * order-preserving — {@code pack(a) <= pack(b)} iff {@code a.compareTo(b) <= 0} — so the store's
 * {@code external_gte} check enforces exactly the lexicographic {@code (epoch, offset)} fence: a
 * higher epoch always wins, an equal version applies idempotently, a stale write is rejected.
 *
 * <p>Bounds: 15 epoch bits allow 32,767 ownership generations (an epoch increments on rebalance /
 * recovery, so thousands of handovers before exhaustion); 48 offset bits allow ~2.8 · 10<sup>14
 * </sup> commit-cut offsets within one epoch. Bit 63 stays clear because the stores require a
 * non-negative version. A version outside these bounds is a configuration-scale event we want to
 * hear about, not wrap around silently — it throws.
 */
final class DocumentVersions {

  private static final int OFFSET_BITS = 48;
  private static final long MAX_EPOCH = (1L << 15) - 1;
  private static final long MAX_OFFSET = (1L << OFFSET_BITS) - 1;

  private DocumentVersions() {}

  static long pack(final WriteVersion version) {
    if (version.epoch() < 0 || version.epoch() > MAX_EPOCH) {
      throw new IllegalArgumentException(
          "write-version epoch " + version.epoch() + " outside [0, " + MAX_EPOCH + "]");
    }
    if (version.offset() < 0 || version.offset() > MAX_OFFSET) {
      throw new IllegalArgumentException(
          "write-version offset " + version.offset() + " outside [0, " + MAX_OFFSET + "]");
    }
    return (version.epoch() << OFFSET_BITS) | version.offset();
  }
}
