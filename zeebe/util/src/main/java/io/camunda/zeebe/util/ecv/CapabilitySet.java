/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.ecv;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An immutable set of capabilities, stored as a bitmask: bit {@code n} is the capability with id
 * {@code n}. A set can hold bits that this binary does not know, for example the enabled set
 * written by a newer binary; every operation keeps them, so an older binary never drops them.
 *
 * <p>The {@link #encode() encoded} form is the bitmask as a lowercase hexadecimal number, which is
 * the same for every binary.
 */
public final class CapabilitySet {
  public static final CapabilitySet EMPTY = new CapabilitySet(new BitSet());

  private final BitSet bits;

  private CapabilitySet(final BitSet bits) {
    this.bits = bits;
  }

  public static CapabilitySet of(final Capability... capabilities) {
    final var bits = new BitSet();
    for (final var capability : capabilities) {
      bits.set(capability.id());
    }
    return new CapabilitySet(bits);
  }

  public static CapabilitySet of(final Collection<Capability> capabilities) {
    return of(capabilities.toArray(Capability[]::new));
  }

  public static CapabilitySet ofIds(final int... ids) {
    final var bits = new BitSet();
    IntStream.of(ids).forEach(bits::set);
    return new CapabilitySet(bits);
  }

  /** Decodes the {@link #encode() encoded} form; an empty string is the empty set. */
  public static CapabilitySet decode(final String encoded) {
    if (encoded.isBlank()) {
      return EMPTY;
    }
    final var number = new BigInteger(encoded, 16);
    final var bits = new BitSet();
    for (int i = 0; i < number.bitLength(); i++) {
      if (number.testBit(i)) {
        bits.set(i);
      }
    }
    return new CapabilitySet(bits);
  }

  public String encode() {
    var number = BigInteger.ZERO;
    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
      number = number.setBit(i);
    }
    return number.toString(16);
  }

  public CapabilitySet union(final CapabilitySet other) {
    final var result = (BitSet) bits.clone();
    result.or(other.bits);
    return new CapabilitySet(result);
  }

  public CapabilitySet intersection(final CapabilitySet other) {
    final var result = (BitSet) bits.clone();
    result.and(other.bits);
    return new CapabilitySet(result);
  }

  /** Returns the capabilities of this set that are not in the other set. */
  public CapabilitySet minus(final CapabilitySet other) {
    final var result = (BitSet) bits.clone();
    result.andNot(other.bits);
    return new CapabilitySet(result);
  }

  /** Returns true if this set contains every capability of the other set. */
  public boolean containsAll(final CapabilitySet other) {
    return other.minus(this).isEmpty();
  }

  public boolean contains(final Capability capability) {
    return bits.get(capability.id());
  }

  public boolean isEmpty() {
    return bits.isEmpty();
  }

  public List<Integer> ids() {
    return bits.stream().boxed().toList();
  }

  /** Returns the bits of this set that this binary's catalog does not know. */
  public CapabilitySet unknown() {
    return minus(Capability.all());
  }

  /**
   * Returns a description of each capability whose prerequisite is not in this set, or an empty
   * list if the set is closed under prerequisites. Unknown bits have no known prerequisites.
   */
  public List<String> missingPrerequisites() {
    final var missing = new ArrayList<String>();
    for (final var capability : Capability.values()) {
      if (!contains(capability)) {
        continue;
      }
      for (final var prerequisite : capability.prerequisites()) {
        if (!contains(prerequisite)) {
          missing.add(capability + " requires " + prerequisite);
        }
      }
    }
    return missing;
  }

  /** Returns the names of the capabilities, with an unknown capability shown by its id. */
  public List<String> names() {
    return bits.stream()
        .mapToObj(id -> Capability.fromId(id).map(Enum::name).orElse("UNKNOWN_" + id))
        .toList();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final CapabilitySet other && bits.equals(other.bits);
  }

  @Override
  public int hashCode() {
    return bits.hashCode();
  }

  @Override
  public String toString() {
    return names().stream().collect(Collectors.joining(", ", "[", "]"));
  }
}
