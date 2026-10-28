/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;

/**
 * A proposed change of a physical tenant's configuration subtree, with property names relative to
 * {@code camunda.physical-tenants.<id>}.
 *
 * @param current the subtree the tenant runs with, or empty for a tenant being created
 * @param proposed the whole subtree the tenant should run with
 */
@NullMarked
public record PhysicalTenantConfigChange(
    String physicalTenantId, Optional<Map<String, String>> current, Map<String, String> proposed) {

  public PhysicalTenantConfigChange {
    current = current.map(Map::copyOf);
    proposed = Map.copyOf(proposed);
  }

  public static PhysicalTenantConfigChange create(
      final String physicalTenantId, final Map<String, String> proposed) {
    return new PhysicalTenantConfigChange(physicalTenantId, Optional.empty(), proposed);
  }

  public static PhysicalTenantConfigChange update(
      final String physicalTenantId,
      final Map<String, String> current,
      final Map<String, String> proposed) {
    return new PhysicalTenantConfigChange(physicalTenantId, Optional.of(current), proposed);
  }

  /** The properties whose value differs, including those only one side sets; empty on create. */
  public Set<String> changedProperties() {
    return current
        .map(
            before -> {
              final var changed = new TreeSet<String>();
              before.forEach(
                  (name, value) -> {
                    if (!value.equals(proposed.get(name))) {
                      changed.add(name);
                    }
                  });
              proposed.keySet().stream()
                  .filter(name -> !before.containsKey(name))
                  .forEach(changed::add);
              return (Set<String>) changed;
            })
        .orElse(Set.of());
  }
}
