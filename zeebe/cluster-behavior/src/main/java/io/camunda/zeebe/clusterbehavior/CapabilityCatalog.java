/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.clusterbehavior;

import io.camunda.zeebe.protocol.EnumValue;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The catalog of capabilities: the single, shared definition of what a binary can support.
 *
 * <p>An entry has an explicit <b>ordinal</b>, which never changes and is never {@link
 * Enum#ordinal()} (the position in the enum), and optionally its <b>requirements</b> (the
 * prerequisites): other entries of this enum, which must be declared before it and have a lower
 * ordinal. javac rejects a reference to a later constant ("illegal forward reference"), which
 * already enforces the declaration order. Ordinals are allocated in sequence on {@code main}. A
 * release line that backports an entry without the ones allocated before it has a gap, which is
 * simply an absent ordinal (there is no entry for it). For example:
 *
 * <pre>{@code
 * JOB_TIMEOUT_OVERFLOW(20),
 *
 * SOME_CAPABILITY(23, JOB_TIMEOUT_OVERFLOW),
 * }</pre>
 *
 * <p>The consistency rules (non-negative ordinals, requirements that have a lower ordinal,
 * declaration order equal to ordinal order) are enforced by unit tests; unique ordinals are
 * enforced when this class is initialized.
 */
// the formatter puts the semicolon of an enum without entries on its own line
@SuppressWarnings("checkstyle:nowhitespacebefore")
public enum CapabilityCatalog implements EnumValue {
  ;

  /**
   * Index for {@link #from(int)} and {@link #isKnown(int)}, derived from {@link #values()}. A
   * duplicate ordinal fails the class initialization ({@link Collectors#toUnmodifiableMap} throws
   * an {@link IllegalStateException}).
   */
  private static final Map<Integer, CapabilityCatalog> BY_ORDINAL = indexByOrdinal();

  private final int capabilityOrdinal;
  private final Set<CapabilityCatalog> requirements;

  /**
   * @param capabilityOrdinal the explicit, stable ordinal of the capability
   * @param requirements the prerequisites: earlier entries of this enum, none by default
   */
  CapabilityCatalog(final int capabilityOrdinal, final CapabilityCatalog... requirements) {
    this.capabilityOrdinal = capabilityOrdinal;
    this.requirements = Set.of(requirements);
  }

  /**
   * @return the explicit, stable ordinal of this capability; the bit position in a set. Never use
   *     {@link Enum#ordinal()} for this.
   */
  @Override
  public int getValue() {
    return capabilityOrdinal;
  }

  /**
   * @return the immutable prerequisites of this capability; all have a lower ordinal
   */
  public Set<CapabilityCatalog> requirements() {
    return requirements;
  }

  private static Map<Integer, CapabilityCatalog> indexByOrdinal() {
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
  public static CapabilityCatalog from(final int capabilityOrdinal) {
    final var capability = BY_ORDINAL.get(capabilityOrdinal);
    if (capability == null) {
      throw new IllegalArgumentException(
          "Expected a capability ordinal of the catalog but got " + capabilityOrdinal);
    }
    return capability;
  }
}
