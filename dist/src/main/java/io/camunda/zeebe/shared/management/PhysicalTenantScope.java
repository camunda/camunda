/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.PhysicalTenantIds;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Turns an actuator's optional {@code physicalTenant} query parameter into the list of physical
 * tenants an operation runs against (ADR 003 D3): the named tenant alone, or every configured
 * tenant when the parameter is absent.
 *
 * <p>Tenants come back in ascending id order so a fan-out and the response it aggregates are
 * reproducible across calls, matching how the {@code cluster} and {@code exporters} actuators order
 * their per-tenant output.
 */
@NullMarked
public final class PhysicalTenantScope {

  private PhysicalTenantScope() {}

  /**
   * @param physicalTenant the requested tenant, or {@code null}/blank for every tenant. Blank
   *     counts as absent because that is what an empty {@code ?physicalTenant=} resolves to, and
   *     scoping to a tenant that cannot exist would be a confusing way to reject it.
   * @throws UnknownPhysicalTenantException if the requested tenant is not configured
   */
  public static List<String> resolve(
      final @Nullable String physicalTenant, final PhysicalTenantIds physicalTenantIds) {
    final Set<String> known = physicalTenantIds.known();
    if (physicalTenant == null || physicalTenant.isBlank()) {
      return known.stream().sorted().toList();
    }
    if (!known.contains(physicalTenant)) {
      throw new UnknownPhysicalTenantException(physicalTenant, known);
    }
    return List.of(physicalTenant);
  }
}
