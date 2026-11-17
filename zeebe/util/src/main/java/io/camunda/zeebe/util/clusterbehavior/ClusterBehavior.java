/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.clusterbehavior;

import io.camunda.zeebe.util.SemanticVersion;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;

/**
 * An immutable set of capability ordinals and the behavior version it was committed with.
 *
 * <p>The set has a compact, canonical form: all ordinals below {@link
 * #firstUnsupportedCapability()} are in the set, and the ones from the word that contains it up are
 * a bitmask of {@code long} words (trailing zero words are dropped). The additional fields ({@code
 * lastSupportedCapability}, {@code firstWord}, {@code lastWord}) are always recomputed from the
 * main fields. The set keeps ordinals that the {@link EngineCapabilityCatalog} of this binary does
 * not list.
 *
 * <p>The behavior version is a semantic version without build metadata, for display only and never
 * used for gating: it is not part of {@link #equals(Object)} and {@link #hashCode()}, which compare
 * the set only. {@link #UNKNOWN_VERSION} stands for a version that is not known.
 */
public final class ClusterBehavior {

  /** The number of bits per word of the bitmask. */
  public static final int WORD_SIZE = Long.SIZE;

  /** The behavior version of a set whose version is not known. */
  public static final SemanticVersion UNKNOWN_VERSION = new SemanticVersion(0, 0, 0, null, null);

  private static final ClusterBehavior EMPTY = new ClusterBehavior(0, new long[0], UNKNOWN_VERSION);

  private final int firstUnsupportedCapability;
  private final long[] capabilityBitmask;
  private final SemanticVersion behaviorVersion;
  private final int lastSupportedCapability;
  private final int firstWord;
  private final int lastWord;

  /** Takes ownership of {@code words}, which must already be canonical. */
  ClusterBehavior(
      final int firstUnsupportedCapability,
      final long[] words,
      final SemanticVersion behaviorVersion) {
    this.firstUnsupportedCapability = firstUnsupportedCapability;
    capabilityBitmask = words;
    final var version = Objects.requireNonNull(behaviorVersion, "behaviorVersion");
    this.behaviorVersion =
        new SemanticVersion(
            version.major(), version.minor(), version.patch(), version.preRelease(), null);
    firstWord = firstUnsupportedCapability / WORD_SIZE;
    if (words.length == 0) {
      lastSupportedCapability = firstUnsupportedCapability - 1;
    } else {
      final long highest = words[words.length - 1];
      lastSupportedCapability =
          (firstWord + words.length - 1) * WORD_SIZE
              + (WORD_SIZE - 1 - Long.numberOfLeadingZeros(highest));
    }
    lastWord = lastSupportedCapability / WORD_SIZE;
  }

  /** The empty set, with an {@link #UNKNOWN_VERSION unknown} behavior version. */
  public static ClusterBehavior empty() {
    return EMPTY;
  }

  /** The set of the given catalog entries with the given behavior version. */
  public static ClusterBehavior of(
      final SemanticVersion behaviorVersion, final EngineCapabilityCatalog... capabilities) {
    final BitSet bits = new BitSet();
    for (final EngineCapabilityCatalog capability : capabilities) {
      bits.set(capability.capabilityOrdinal());
    }
    return of(behaviorVersion, bits);
  }

  /**
   * The set of the enabled ordinals in {@code enabledOrdinals} with the given behavior version. It
   * keeps ordinals that the catalog of this binary does not list.
   */
  public static ClusterBehavior of(
      final SemanticVersion behaviorVersion, final BitSet enabledOrdinals) {
    // the first clear bit is the first unsupported capability; toLongArray has no trailing zero
    // words, so dropping the words below it gives the canonical bitmask
    final int first = enabledOrdinals.nextClearBit(0);
    final long[] all = enabledOrdinals.toLongArray();
    return new ClusterBehavior(
        first, Arrays.copyOfRange(all, first / WORD_SIZE, all.length), behaviorVersion);
  }

  /** The lowest ordinal not in the set; every lower one is in it. */
  public int firstUnsupportedCapability() {
    return firstUnsupportedCapability;
  }

  /** A copy of the stored words, from {@link #firstWord()} to {@link #lastWord()}. */
  public long[] capabilityBitmask() {
    return capabilityBitmask.clone();
  }

  /** The behavior version of the set, never null; build metadata is not kept. */
  public SemanticVersion behaviorVersion() {
    return behaviorVersion;
  }

  /** The highest ordinal in the set, or -1 for the empty set. */
  public int lastSupportedCapability() {
    return lastSupportedCapability;
  }

  /** The index of the first stored word. */
  public int firstWord() {
    return firstWord;
  }

  /** The index of the last stored word. */
  public int lastWord() {
    return lastWord;
  }

  /** Whether the set contains the given capability. */
  public boolean isCapabilityEnabled(final EngineCapabilityCatalog capability) {
    return isCapabilityEnabled(capability.capabilityOrdinal());
  }

  /**
   * Whether the set contains the given explicit ordinal.
   *
   * @param capability a non-negative ordinal, not {@link Enum#ordinal()}
   */
  public boolean isCapabilityEnabled(final int capability) {
    if (capability < 0) {
      throw new IllegalArgumentException("ordinal must not be negative: " + capability);
    }
    if (capability < firstUnsupportedCapability) {
      return true;
    }
    if (capability > lastSupportedCapability) {
      return false;
    }
    return (capabilityBitmask[capability / WORD_SIZE - firstWord]
            & (1L << (capability % WORD_SIZE)))
        != 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final ClusterBehavior that)) {
      return false;
    }
    return firstUnsupportedCapability == that.firstUnsupportedCapability
        && Arrays.equals(capabilityBitmask, that.capabilityBitmask);
  }

  @Override
  public int hashCode() {
    return 31 * firstUnsupportedCapability + Arrays.hashCode(capabilityBitmask);
  }

  /**
   * The behavior version, {@code firstUnsupportedCapability} and {@code lastSupportedCapability}.
   */
  @Override
  public String toString() {
    return "ClusterBehavior{behaviorVersion="
        + behaviorVersion
        + ", firstUnsupportedCapability="
        + firstUnsupportedCapability
        + ", lastSupportedCapability="
        + lastSupportedCapability
        + "}";
  }
}
