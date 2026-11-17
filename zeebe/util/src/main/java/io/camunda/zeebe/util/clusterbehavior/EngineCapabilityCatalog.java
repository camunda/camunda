/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.clusterbehavior;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The catalog of engine capabilities: the single, shared definition of what a binary can support.
 *
 * <p>An entry has an explicit <b>ordinal</b>, which never changes and is never {@link
 * Enum#ordinal()} (the position in the enum), and optionally its <b>requirements</b> (the
 * prerequisites): other entries of this enum, which must be declared before it and have a lower
 * ordinal. javac rejects a reference to a later constant ("illegal forward reference"), which
 * already enforces the declaration order. Ordinals are allocated in sequence on {@code main}; a gap
 * in the catalog of a release line is only caused by a backport, and is simply an absent ordinal
 * (there is no entry for it). For example:
 *
 * <pre>{@code
 * JOB_TIMEOUT_OVERFLOW(20),
 *
 * SOME_CAPABILITY(23, JOB_TIMEOUT_OVERFLOW),
 * }</pre>
 *
 * <p>The engineers working on the engine own this catalog and allocate the ordinals. The
 * consistency rules (non-negative ordinals, requirements that have a lower ordinal, declaration
 * order equal to ordinal order) are enforced by unit tests; unique ordinals are enforced when this
 * class is initialized.
 */
// the formatter puts the semicolon of an enum without entries on its own line
@SuppressWarnings("checkstyle:nowhitespacebefore")
public enum EngineCapabilityCatalog {
  ;

  /**
   * The entries of the catalog by their explicit ordinal, for {@link #from(int)} and {@link
   * #isKnown(int)}.
   *
   * <p>It is built from {@link #values()} when this class is initialized, so the declaration of the
   * entries is the single source of truth and there is no second, hand-maintained mapping to keep
   * in sync. This is the same idea as the {@code from} method of an {@code Intent} enum, which maps
   * a wire value to its constant; here the mapping is derived instead of written as a switch, and a
   * missing entry is an error instead of an {@code UNKNOWN} constant.
   *
   * <p>Two entries with the same ordinal make the class initialization fail ({@link
   * java.util.stream.Collectors#toUnmodifiableMap} throws an {@link IllegalStateException} on a
   * duplicate key), so a broken catalog never loads.
   */
  private static final Map<Integer, EngineCapabilityCatalog> BY_ORDINAL = indexByOrdinal();

  private final int capabilityOrdinal;
  private final Set<EngineCapabilityCatalog> requirements;

  /**
   * @param capabilityOrdinal the explicit, stable ordinal of the capability
   * @param requirements the prerequisites: earlier entries of this enum, none by default
   */
  EngineCapabilityCatalog(
      final int capabilityOrdinal, final EngineCapabilityCatalog... requirements) {
    this.capabilityOrdinal = capabilityOrdinal;
    this.requirements = Set.copyOf(Arrays.asList(requirements));
  }

  /**
   * @return the explicit, stable ordinal of this capability; the bit position in a set. Never use
   *     {@link Enum#ordinal()} for this.
   */
  public int capabilityOrdinal() {
    return capabilityOrdinal;
  }

  /**
   * @return the immutable prerequisites of this capability; all have a lower ordinal
   */
  public Set<EngineCapabilityCatalog> requirements() {
    return requirements;
  }

  private static Map<Integer, EngineCapabilityCatalog> indexByOrdinal() {
    return Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(c -> c.capabilityOrdinal, Function.identity()));
  }

  /**
   * @return whether the catalog of this binary has an entry with the given explicit ordinal
   */
  public static boolean isKnown(final int capabilityOrdinal) {
    return BY_ORDINAL.containsKey(capabilityOrdinal);
  }

  /**
   * @return the capability with the given explicit ordinal
   * @throws IllegalArgumentException if the catalog of this binary has no entry for it; use {@link
   *     #isKnown(int)} to check first
   */
  public static EngineCapabilityCatalog from(final int capabilityOrdinal) {
    final var capability = BY_ORDINAL.get(capabilityOrdinal);
    if (capability == null) {
      throw new IllegalArgumentException(
          "Expected a capability ordinal of the catalog but got " + capabilityOrdinal);
    }
    return capability;
  }
}
