/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.ecv;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The shared capability catalog: every capability that this binary supports. Each capability is one
 * bit of a {@link CapabilitySet}; its {@link #id()} is the bit position and never changes.
 *
 * <p>POC: the 8.12 entries are placeholders that let the commit and the refusal be exercised; they
 * gate no behavior.
 */
public enum Capability {
  /** The capability framework itself; part of the base set of every 8.11 cluster. */
  ECV_FRAMEWORK(0, "8.11", "The engine capability framework and the system partition"),
  /** POC placeholder for a format change shipped in 8.12. */
  POC_812_FORMAT_CHANGE(1, "8.12", "POC: a format change that 8.11 cannot read"),
  /** POC placeholder for an 8.12 behavior that needs {@link #POC_812_FORMAT_CHANGE}. */
  POC_812_DEPENDENT_BEHAVIOR(
      2, "8.12", "POC: a behavior that needs the 8.12 format change", POC_812_FORMAT_CHANGE);

  /** The minor of the base set: the set for data without an ECV. */
  public static final String BASE_MINOR = "8.11";

  private final int id;
  private final String introducedIn;
  private final String description;
  private final List<Capability> prerequisites;

  Capability(
      final int id,
      final String introducedIn,
      final String description,
      final Capability... prerequisites) {
    this.id = id;
    this.introducedIn = introducedIn;
    this.description = description;
    this.prerequisites = List.of(prerequisites);
  }

  public int id() {
    return id;
  }

  public String introducedIn() {
    return introducedIn;
  }

  public String description() {
    return description;
  }

  public List<Capability> prerequisites() {
    return prerequisites;
  }

  /**
   * Returns the capability with the given bit position, or empty if this binary does not know it.
   */
  public static Optional<Capability> fromId(final int id) {
    return Arrays.stream(values()).filter(capability -> capability.id == id).findFirst();
  }

  /** Returns every capability that this binary supports. */
  public static CapabilitySet all() {
    return CapabilitySet.of(values());
  }

  /** Returns the base set: the set for data without an ECV. It is not the initial set (R29). */
  public static CapabilitySet base() {
    return CapabilitySet.of(
        Arrays.stream(values())
            .filter(capability -> capability.introducedIn.equals(BASE_MINOR))
            .toArray(Capability[]::new));
  }
}
