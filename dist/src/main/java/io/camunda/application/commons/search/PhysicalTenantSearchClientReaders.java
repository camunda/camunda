/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static io.camunda.configuration.physicaltenants.PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.search.clients.reader.SearchClientReaders;
import java.util.Map;

/**
 * Holds the {@link SearchClientReaders} for every physical tenant backed by a single storage family
 * (ES/OS or RDBMS), keyed by physical tenant id.
 *
 * <p>A typed wrapper is required because injecting a bare {@code Map<String, SearchClientReaders>}
 * would trigger Spring's collection-injection semantics: Spring would look for beans of type {@code
 * SearchClientReaders} and key them by bean name rather than by physical tenant id.
 */
public record PhysicalTenantSearchClientReaders(
    Map<String, SearchClientReaders> readersByPhysicalTenant) {

  /**
   * Returns the per-tenant readers, asserting the global {@code default} physical tenant is
   * present. The default tenant always maps to the global secondary-storage configuration, so its
   * absence indicates a wiring error.
   */
  public Map<String, SearchClientReaders> requireDefaultTenant() {
    if (!readersByPhysicalTenant.containsKey(DEFAULT_PHYSICAL_TENANT_ID)) {
      throw new IllegalStateException(
          "Missing '%s' physical tenant; the global secondary-storage configuration must provide it. Known physical tenants: %s"
              .formatted(DEFAULT_PHYSICAL_TENANT_ID, readersByPhysicalTenant.keySet()));
    }
    return readersByPhysicalTenant;
  }
}
