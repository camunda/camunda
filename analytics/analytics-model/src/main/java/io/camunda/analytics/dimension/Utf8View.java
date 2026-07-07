/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * An immutable string value carried as its UTF-8 bytes (ADR 0008): the form a fact's string-typed
 * fields take when the source already had the bytes (record payload slices, projected variable
 * values), so the hot path never decodes to {@code String}. Equality and hash are byte-wise — UTF-8
 * byte equality is string equality — and {@link #toString()} materializes lazily (memoized) at the
 * mandatory edges only: JDBC binds, top-k sketch items, JSON, logging.
 *
 * <p>The dimension-key encoder writes the bytes straight through, distinct-count sketches hash them
 * directly (bit-identical to hashing the {@code String}), and filters compare them against a
 * pre-encoded filter value.
 */
public final class Utf8View {

  private final byte[] utf8;
  private int hash; // lazily computed; 0 means "not yet" (recomputed if it legitimately hashes 0)
  private String string; // lazily decoded

  private Utf8View(final byte[] utf8) {
    this.utf8 = utf8;
  }

  /** Wraps {@code utf8}, taking ownership — the caller must never mutate the array afterwards. */
  public static Utf8View wrap(final byte[] utf8) {
    return new Utf8View(Objects.requireNonNull(utf8, "utf8"));
  }

  /** Copies {@code buffer}'s readable bytes into an owned view. */
  public static Utf8View copyOf(final DirectBuffer buffer) {
    return copyOf(buffer, 0, buffer.capacity());
  }

  /** Copies {@code length} bytes at {@code offset} of {@code buffer} into an owned view. */
  public static Utf8View copyOf(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] utf8 = new byte[length];
    buffer.getBytes(offset, utf8);
    return new Utf8View(utf8);
  }

  /** Encodes a {@code String} — the test/edge constructor; equal to a byte-sourced view. */
  public static Utf8View of(final String value) {
    final Utf8View view = new Utf8View(value.getBytes(StandardCharsets.UTF_8));
    view.string = value;
    return view;
  }

  /** The backing UTF-8 bytes — callers must not mutate them. */
  public byte[] utf8() {
    return utf8;
  }

  public int length() {
    return utf8.length;
  }

  public boolean isEmpty() {
    return utf8.length == 0;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final Utf8View other && Arrays.equals(utf8, other.utf8);
  }

  @Override
  public int hashCode() {
    int result = hash;
    if (result == 0) {
      result = Arrays.hashCode(utf8);
      hash = result;
    }
    return result;
  }

  @Override
  public String toString() {
    String result = string;
    if (result == null) {
      result = new String(utf8, StandardCharsets.UTF_8);
      string = result;
    }
    return result;
  }
}
