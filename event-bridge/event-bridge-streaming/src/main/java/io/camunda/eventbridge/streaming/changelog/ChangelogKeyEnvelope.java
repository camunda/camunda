/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import java.util.Arrays;

/**
 * A compact, self-describing key envelope for a shard whose changelog (streaming ADR 0009 Decisions
 * 1/2) carries rows from <em>several</em> column families sharing one changelog topic — e.g. the
 * analytics Stage-1 projection changelog, which replicates the base projection's caching stores
 * (element, variable, incident, variant-element rows) and the sealing aggregation's open-segment
 * cells side by side. A shard whose changelog is backed by a single column family (e.g. Stage 2's
 * merged cube cells, keyed directly by {@code GroupedCellStore}) needs no envelope at all: its own
 * store key already is the whole changelog partition's keyspace.
 *
 * <p><b>Layout: {@code cfTag(1 byte, unsigned) ++ storeKey}.</b> {@code cfTag} is the column
 * family's own already-stable identity (e.g. {@code AnalyticsColumnFamilies#getValue()}) — reusing
 * it avoids inventing a second numbering that must be kept in sync with the first. {@code storeKey}
 * is the store's own row key bytes, unchanged. <b>The byte layout is durable identity — never
 * change it</b>: every non-marker key in a live changelog partition was written under exactly these
 * bytes; changing the framing orphans it.
 *
 * <p><b>Collision-proofing against {@link ChangelogMarker}.</b> {@link ChangelogMarker#KEY} is
 * exactly 4 bytes. {@link #encode} requires {@code storeKey.length >= 4} — true of every store key
 * enveloped today (the shortest is a bare 4-byte {@code GroupedCellStore} meta row or a 4-byte
 * {@code DbInt} key), so every enveloped key is at least 5 bytes long: strictly longer than the
 * marker's 4, and therefore never equal to it, by length alone, for any legal {@code (cfTag,
 * storeKey)} pair. This generalizes the same argument {@code GroupedCellStore}'s meta row makes
 * against a cell row (a shorter reserved-shape key that cannot collide with a longer one) — a tag
 * byte in place of a group id, and a length floor in place of an exact reserved value.
 */
public final class ChangelogKeyEnvelope {

  /**
   * The floor a store key must meet so an enveloped key can never equal {@link
   * ChangelogMarker#KEY}.
   */
  private static final int MIN_STORE_KEY_LENGTH = ChangelogMarker.KEY.length;

  private static final int CF_TAG_LENGTH = Byte.BYTES;
  private static final int MAX_CF_TAG = 0xFF;

  private ChangelogKeyEnvelope() {}

  /**
   * Encodes {@code storeKey} under {@code cfTag}: {@code cfTag(1 byte) ++ storeKey}.
   *
   * @throws IllegalArgumentException if {@code cfTag} does not fit an unsigned byte, or {@code
   *     storeKey} is shorter than {@value #MIN_STORE_KEY_LENGTH} bytes (the floor that keeps every
   *     enveloped key collision-proof against the reserved changelog marker key)
   */
  public static byte[] encode(final int cfTag, final byte[] storeKey) {
    if (cfTag < 0 || cfTag > MAX_CF_TAG) {
      throw new IllegalArgumentException(
          "expected a cfTag in [0, " + MAX_CF_TAG + "], was " + cfTag);
    }
    if (storeKey.length < MIN_STORE_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "expected a store key of at least "
              + MIN_STORE_KEY_LENGTH
              + " bytes (so the enveloped key can never collide with the reserved changelog"
              + " marker key), was "
              + storeKey.length);
    }
    final byte[] envelope = new byte[CF_TAG_LENGTH + storeKey.length];
    envelope[0] = (byte) cfTag;
    System.arraycopy(storeKey, 0, envelope, CF_TAG_LENGTH, storeKey.length);
    return envelope;
  }

  /**
   * Decodes an enveloped key back into its column-family tag and store key bytes.
   *
   * @throws IllegalArgumentException if {@code key} is too short to be a legal envelope
   */
  public static Envelope decode(final byte[] key) {
    if (key.length < CF_TAG_LENGTH + MIN_STORE_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "expected an enveloped key of at least "
              + (CF_TAG_LENGTH + MIN_STORE_KEY_LENGTH)
              + " bytes, was "
              + key.length);
    }
    final int cfTag = key[0] & MAX_CF_TAG;
    final byte[] storeKey = Arrays.copyOfRange(key, CF_TAG_LENGTH, key.length);
    return new Envelope(cfTag, storeKey);
  }

  /** A decoded envelope: the column-family tag and the store's own row key bytes. */
  public record Envelope(int cfTag, byte[] storeKey) {}
}
