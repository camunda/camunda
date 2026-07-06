/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.env.Environment;

/**
 * Central registry of the typed-POJO map configs that are deep-merged per physical tenant. Adding a
 * per-tenant map overlay for a new config root means adding a {@link MapOverlaySpec} here — not new
 * merge code. Exporter {@code args} ({@code Map<String, Object>}, raw) deliberately stays on its
 * own deep-merge path: its value type cannot be field-enumerated, so it is neither registered nor
 * per-field guarded.
 */
@NullMarked
final class PhysicalTenantMapOverlays {

  /**
   * Single source of truth for the deep-merged map surface: consumed by {@link #apply} for the
   * merge and by the override golden test to guard the per-field surface.
   */
  static final List<MapOverlaySpec<?>> REGISTRY =
      List.of(
          PhysicalTenantDocumentConfigurations.SPEC,
          PhysicalTenantAuthenticationProviderConfigurations.SPEC);

  private PhysicalTenantMapOverlays() {}

  /**
   * Recomputes every registered config root for the given tenant with the merge-aware engine and
   * installs the result on the tenant's {@link Camunda}. Called by {@link PhysicalTenantResolver}
   * after the generic two-bind, which leaves registered maps with the entry-replacement defect.
   */
  static void apply(
      final Camunda physicalTenant, final String tenantId, final Environment environment) {
    for (final MapOverlaySpec<?> spec : REGISTRY) {
      applyOne(spec, physicalTenant, tenantId, environment);
    }
  }

  private static <R> void applyOne(
      final MapOverlaySpec<R> spec,
      final Camunda physicalTenant,
      final String tenantId,
      final Environment environment) {
    spec.installer()
        .accept(physicalTenant, PhysicalTenantMapOverlay.overlay(spec, tenantId, environment));
  }
}
