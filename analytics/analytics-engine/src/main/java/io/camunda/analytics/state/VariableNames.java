/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The precomputed set of variable names an enrichment read targets: each name's UTF-8 bytes (the
 * variable-store key suffix) are encoded once, when the topology is installed, instead of once per
 * point lookup on the hot path (ADR 0008). Built from the union of {@code var.*} names any active
 * dataset groups or filters by; immutable and shareable.
 */
public final class VariableNames {

  public static final VariableNames NONE = new VariableNames(List.of());

  /** One requested name: the declared name and its UTF-8 bytes (never mutated). */
  public record Name(String name, byte[] utf8) {}

  private final List<Name> names;

  private VariableNames(final List<Name> names) {
    this.names = List.copyOf(names);
  }

  /** Precomputes the UTF-8 bytes of each distinct name, preserving iteration order. */
  public static VariableNames of(final Collection<String> names) {
    final Set<String> distinct = new LinkedHashSet<>(names);
    final List<Name> encoded = new ArrayList<>(distinct.size());
    for (final String name : distinct) {
      encoded.add(new Name(name, name.getBytes(StandardCharsets.UTF_8)));
    }
    return new VariableNames(encoded);
  }

  public List<Name> names() {
    return names;
  }

  public int size() {
    return names.size();
  }

  public boolean isEmpty() {
    return names.isEmpty();
  }
}
