/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageAvailability;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * {@link SecondaryStorageAvailability} pulled from schema-initialization state: a physical tenant's
 * secondary storage is available exactly when its schema has finished initializing.
 *
 * <p>A standalone facade over the existing per-tenant schema-init predicate — it holds no mutable
 * state of its own. The predicate is backed by {@code SchemaManagerContainer::isInitialized}
 * (Elasticsearch/OpenSearch) or {@code RdbmsSchemaManagerRegistry::isInitialized} (RDBMS), wired by
 * {@link SecondaryStorageAvailabilityConfiguration}.
 *
 * <p>{@link #anyAvailable()} iterates {@link PhysicalTenantIds#known()} rather than reusing {@code
 * SchemaManagerContainer#isInitialized()}: that method aggregates over <em>all</em> tenants and
 * reports {@code false} on an empty tenant map, which is the wrong semantics for "is there at least
 * one usable tenant". {@code known()} is never empty — {@code PhysicalTenantResolver} always falls
 * back to the {@value PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} tenant.
 */
@NullMarked
public class SchemaInitializationSecondaryStorageAvailability
    implements SecondaryStorageAvailability {

  private final PhysicalTenantIds tenantIds;
  private final Predicate<String> schemaInitialized;

  public SchemaInitializationSecondaryStorageAvailability(
      final PhysicalTenantIds tenantIds, final Predicate<String> schemaInitialized) {
    this.tenantIds = tenantIds;
    this.schemaInitialized = schemaInitialized;
  }

  @Override
  public boolean isAvailable(final String physicalTenantId) {
    return schemaInitialized.test(physicalTenantId);
  }

  @Override
  public boolean anyAvailable() {
    return tenantIds.known().stream().anyMatch(this::isAvailable);
  }
}
