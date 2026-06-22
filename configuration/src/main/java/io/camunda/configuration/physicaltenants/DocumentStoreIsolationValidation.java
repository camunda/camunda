/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule (#54366): no two physical tenants may resolve a document store to the same
 * physical location ({@link DocumentStoreLocation}).
 *
 * <p>Two tenants sharing a store location would silently write into the same bucket/path — the
 * document analogue of the secondary-storage double-write footgun (see {@link
 * SecondaryStorageIsolationValidation}). Comparing <em>fully resolved</em> locations means a
 * non-default tenant that inherits a store unchanged collides with {@code default} too, which is
 * the intended cost of the inherit-and-override model: a tenant must either override the path or
 * drop the store via {@code assigned}. Hard-fail, no opt-out. The synthesized {@code default}
 * tenant participates like any other; single-tenant deployments are a no-op.
 *
 * <p>The collision check runs <em>after</em> per-tenant resolution (so {@code assigned}-restricted
 * stores are already removed and cannot trigger a false collision). Locations are grouped across
 * all tenants and any location used by more than one tenant is reported as a single error naming
 * the colliding tenants.
 */
@NullMarked
class DocumentStoreIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant (the common single-tenant deployment) cannot collide with anything
      return;
    }

    final Map<DocumentStoreLocation, Set<String>> tenantsByLocation = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) ->
            locationsOf(camunda.getDocument())
                .forEach(
                    location ->
                        tenantsByLocation
                            .computeIfAbsent(location, k -> new LinkedHashSet<>())
                            .add(tenantId)));

    final List<String> collisions = new ArrayList<>();
    tenantsByLocation.forEach(
        (location, tenantIds) -> {
          if (tenantIds.size() > 1) {
            collisions.add(
                String.format(
                    "tenants %s share the same document-store location [%s]",
                    tenantIds, location.describe()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share a document-store location, or they would write into the "
              + "same bucket/path. Use a distinct bucket/container, a distinct path/prefix per "
              + "tenant, or drop the store via 'document.assigned'. Conflicts: "
              + String.join("; ", collisions));
    }
  }

  /**
   * The location of every store with a defined location identity. In-memory stores are skipped:
   * they are ephemeral per-instance objects that can never collide.
   */
  private static List<DocumentStoreLocation> locationsOf(final Document doc) {
    final List<DocumentStoreLocation> locations = new ArrayList<>();
    doc.getAws().values().forEach(store -> locations.add(DocumentStoreLocation.aws(store)));
    doc.getGcp().values().forEach(store -> locations.add(DocumentStoreLocation.gcp(store)));
    doc.getAzure().values().forEach(store -> locations.add(DocumentStoreLocation.azure(store)));
    doc.getLocal().values().forEach(store -> locations.add(DocumentStoreLocation.local(store)));
    return locations;
  }
}
