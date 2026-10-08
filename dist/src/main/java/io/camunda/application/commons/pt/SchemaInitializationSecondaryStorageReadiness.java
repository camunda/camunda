/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * {@link SecondaryStorageReadiness} pulled from schema-initialization state: a physical tenant's
 * secondary storage is ready once its schema has finished initializing, unless the tenant is in
 * recovery mode.
 *
 * <p>A standalone facade over the existing per-tenant schema-init predicate — it holds no mutable
 * state of its own. The predicate is backed by {@code SchemaManagerContainer::isInitialized}
 * (Elasticsearch/OpenSearch) or {@code RdbmsSchemaManagerRegistry::isInitialized} (RDBMS), wired by
 * {@link SecondaryStorageReadinessConfiguration}.
 *
 * <p>{@link #anyReady()} iterates {@link PhysicalTenantIds#known()} rather than reusing {@code
 * SchemaManagerContainer#isInitialized()}: that method aggregates over <em>all</em> tenants and
 * reports {@code false} on an empty tenant map, which is the wrong semantics for "is there at least
 * one usable tenant". {@code known()} is never empty — {@code PhysicalTenantResolver} always falls
 * back to the {@value PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} tenant.
 */
@NullMarked
public class SchemaInitializationSecondaryStorageReadiness implements SecondaryStorageReadiness {

  private final PhysicalTenantIds tenantIds;
  private final Predicate<String> schemaInitialized;
  private final Predicate<String> recovering;
  private final Supplier<Map<String, SchemaInitializationStatus>> statuses;

  public SchemaInitializationSecondaryStorageReadiness(
      final PhysicalTenantIds tenantIds,
      final Predicate<String> schemaInitialized,
      final Predicate<String> recovering,
      final Supplier<Map<String, SchemaInitializationStatus>> statuses) {
    this.tenantIds = tenantIds;
    this.schemaInitialized = schemaInitialized;
    this.recovering = recovering;
    this.statuses = statuses;
  }

  @Override
  public boolean isReady(final String physicalTenantId) {
    return schemaInitialized.test(physicalTenantId) && !isRecovering(physicalTenantId);
  }

  @Override
  public boolean anyReady() {
    return tenantIds.known().stream().anyMatch(this::isReady);
  }

  @Override
  public boolean isRecovering(final String physicalTenantId) {
    return recovering.test(physicalTenantId);
  }

  /** A tenant in recovery mode counts as recovering, even once its schema is initialized. */
  @Override
  public NodeReadiness nodeReadiness() {
    return rollUp(
        statuses.get().entrySet().stream()
            .map(
                tenant ->
                    isRecovering(tenant.getKey()) ? State.RECOVERING : tenant.getValue().state())
            .toList());
  }

  /** READY if every tenant is, NOT_READY if every tenant is, DEGRADED otherwise. */
  static NodeReadiness rollUp(final Collection<State> states) {
    var allReady = true;
    var allNotReady = !states.isEmpty();
    for (final var state : states) {
      final var readiness = readinessOf(state);
      allReady &= readiness == NodeReadiness.READY;
      allNotReady &= readiness == NodeReadiness.NOT_READY;
    }
    if (allReady) {
      return NodeReadiness.READY;
    }
    return allNotReady ? NodeReadiness.NOT_READY : NodeReadiness.DEGRADED;
  }

  /** One tenant's readiness: still trying or recovering is DEGRADED, stopped for good NOT_READY. */
  static NodeReadiness readinessOf(final State state) {
    return switch (state) {
      case INITIALIZED -> NodeReadiness.READY;
      case INITIALIZING, RETRYING, RECOVERING -> NodeReadiness.DEGRADED;
      case FAILED, GAVE_UP, ABORTED -> NodeReadiness.NOT_READY;
    };
  }
}
