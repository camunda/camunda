/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.clusterbehavior;

import io.camunda.zeebe.util.SemanticVersion;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The cluster behavior: the capabilities that are enabled in a cluster, and the human-readable
 * behavior version that serves as a reference for them. It is immutable.
 *
 * <p>A capability is identified by its explicit ordinal (see {@link CapabilityCatalog}). The
 * enabled capabilities are represented by {@link #firstUnsupportedCapability()} and {@link
 * #capabilityBitmask()}:
 *
 * <ul>
 *   <li>Every ordinal below {@link #firstUnsupportedCapability()} is enabled. These are implied and
 *       not stored.
 *   <li>The other ordinals are stored as a bitmask of whole 64-bit words, starting at the word that
 *       contains {@link #firstUnsupportedCapability()}. The bits below it are set to one, and
 *       trailing zero words are dropped.
 * </ul>
 *
 * <p>This canonical form means that equal behaviors have identical fields. The other values ({@code
 * lastSupportedCapability}, {@code firstWord}, {@code lastWord}) are derived from these. A cluster
 * behavior keeps ordinals that the {@link CapabilityCatalog} of this binary does not list.
 *
 * <p>The behavior version is a semantic version without pre-release and build metadata (it is only
 * meaningful for a released version), for display only and never used for gating: it is not part of
 * {@link #equals(Object)} and {@link #hashCode()}, which compare the set only. {@link
 * #UNKNOWN_VERSION} stands for a version that is not known.
 */
public final class ClusterBehavior {

  /** The behavior version of a set whose version is not known. */
  public static final SemanticVersion UNKNOWN_VERSION = new SemanticVersion(0, 0, 0, null, null);

  private static final ClusterBehavior EMPTY = new ClusterBehavior(0, new long[0], UNKNOWN_VERSION);

  private final int firstUnsupportedCapability;
  private final long[] capabilityBitmask;
  private final SemanticVersion behaviorVersion;
  private final int lastSupportedCapability;
  private final int firstWord;
  private final int lastWord;

  // the factories pass a fresh array in the canonical form, so it is neither copied nor validated
  private ClusterBehavior(
      final int firstUnsupportedCapability,
      final long[] capabilityBitmask,
      final SemanticVersion behaviorVersion) {
    this.firstUnsupportedCapability = firstUnsupportedCapability;
    this.capabilityBitmask = capabilityBitmask;
    this.behaviorVersion = behaviorVersion.withoutPreRelease();
    firstWord = firstUnsupportedCapability / Long.SIZE;
    lastWord = firstWord + capabilityBitmask.length - 1;
    if (capabilityBitmask.length == 0) {
      lastSupportedCapability = firstUnsupportedCapability - 1;
    } else {
      // the last word always has a bit set, so the highest one is in it
      final long highestWord = capabilityBitmask[capabilityBitmask.length - 1];
      lastSupportedCapability =
          lastWord * Long.SIZE + (Long.SIZE - 1 - Long.numberOfLeadingZeros(highestWord));
    }
  }

  /** The empty set, with an {@link #UNKNOWN_VERSION unknown} behavior version. */
  public static ClusterBehavior empty() {
    return EMPTY;
  }

  /** The set of the given catalog entries with the given behavior version. */
  public static ClusterBehavior of(
      final SemanticVersion behaviorVersion, final CapabilityCatalog... capabilities) {
    return ofOrdinals(
        behaviorVersion,
        Arrays.stream(capabilities).mapToInt(CapabilityCatalog::getValue).toArray());
  }

  /**
   * The set of the given ordinals, in any order and with duplicates allowed, with the given
   * behavior version. It keeps ordinals that the catalog of this binary does not list.
   */
  static ClusterBehavior ofOrdinals(final SemanticVersion behaviorVersion, final int... ordinals) {
    final int[] sorted = ordinals.clone();
    Arrays.sort(sorted);
    if (sorted.length > 0 && sorted[0] < 0) {
      throw new IllegalArgumentException("ordinal must not be negative: " + sorted[0]);
    }

    // first pass: the first unsupported ordinal is the first one missing from 0, 1, 2, ...
    int first = 0;
    for (final int ordinal : sorted) {
      if (ordinal == first) {
        first++;
      } else if (ordinal > first) {
        // a gap: first is missing, and duplicates (ordinal < first) are skipped
        break;
      }
    }
    final int last = sorted.length == 0 ? -1 : sorted[sorted.length - 1];

    // second pass: the words from the one that contains first up to the one of the last ordinal
    final int firstWord = first / Long.SIZE;
    final long[] words = new long[last < 0 ? 0 : last / Long.SIZE - firstWord + 1];
    if (words.length > 0) {
      words[0] = (1L << (first % Long.SIZE)) - 1;
    }
    for (final int ordinal : sorted) {
      if (ordinal > first) {
        words[ordinal / Long.SIZE - firstWord] |= 1L << (ordinal % Long.SIZE);
      }
    }
    return new ClusterBehavior(first, words, behaviorVersion);
  }

  /** The lowest ordinal not in the set; every lower one is in it. */
  public int firstUnsupportedCapability() {
    return firstUnsupportedCapability;
  }

  /** A copy of the stored words, from {@link #firstWord()} to {@link #lastWord()}. */
  public long[] capabilityBitmask() {
    return capabilityBitmask.clone();
  }

  /** The behavior version of the set, never null; pre-release and build metadata are not kept. */
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
  public boolean isCapabilityEnabled(final CapabilityCatalog capability) {
    return isCapabilityEnabled(capability.getValue());
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
    return (capabilityBitmask[capability / Long.SIZE - firstWord]
            & (1L << (capability % Long.SIZE)))
        != 0;
  }

  @Override
  public boolean equals(final @Nullable Object o) {
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
    return Objects.hash(firstUnsupportedCapability, Arrays.hashCode(capabilityBitmask));
  }

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
