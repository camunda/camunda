/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * SPIKE (ADR-0005): a physical-tenant-routing {@link AuthorizationReader}.
 *
 * <p>Resolves the physical tenant in context on each call and delegates to that tenant's reader, so
 * authorization reads target the in-context tenant's secondary storage rather than a single
 * {@code default}-pinned reader (#55252). Because both authorization-read consumers inject the same
 * {@code AuthorizationReader} bean — the control-plane {@code AuthorizationRepositoryAdapter} (Spring
 * Security permission checks) and the data-plane {@code SearchAuthorizationScopeRepository}
 * ({@code CamundaSearchClients} result authorization) — re-pointing that one bean here makes both
 * paths physical-tenant aware.
 *
 * <p>This implements the ADR-0005 <b>control-plane</b> mechanism: lazy resolution via {@link
 * PhysicalTenantContext#current()}. The pre-security filter (ADR-0003) guarantees the id is stamped
 * on the request before the check runs, and {@code current()} falls back to {@code default} for
 * non-prefixed cluster requests.
 *
 * <p><b>Spike caveat:</b> on the data-plane this relies on the thread-bound {@link
 * PhysicalTenantContext} being present where the check executes. If a search runs off the request
 * thread (e.g. the API-services executor), the thread-local would fall back to {@code default}. The
 * ADR-0005 <b>data-plane</b> decision (instance-bound resolution via the {@code CamundaSearchClients}
 * instance's tenant) addresses that and is the larger structural change this spike deliberately
 * does not yet make — see the spike notes.
 */
@NullMarked
public final class PhysicalTenantRoutingAuthorizationReader implements AuthorizationReader {

  private final Map<String, AuthorizationReader> readersByPhysicalTenant;
  private final Supplier<String> physicalTenantIdSupplier;

  public PhysicalTenantRoutingAuthorizationReader(
      final Map<String, AuthorizationReader> readersByPhysicalTenant) {
    this(readersByPhysicalTenant, PhysicalTenantContext::current);
  }

  PhysicalTenantRoutingAuthorizationReader(
      final Map<String, AuthorizationReader> readersByPhysicalTenant,
      final Supplier<String> physicalTenantIdSupplier) {
    this.readersByPhysicalTenant = Map.copyOf(readersByPhysicalTenant);
    this.physicalTenantIdSupplier = physicalTenantIdSupplier;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(
      final AuthorizationQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return current().search(query, resourceAccessChecks);
  }

  @Override
  public AuthorizationEntity getById(
      final String id, final ResourceAccessChecks resourceAccessChecks) {
    return current().getById(id, resourceAccessChecks);
  }

  @Override
  public AuthorizationEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return current().getByKey(key, resourceAccessChecks);
  }

  private AuthorizationReader current() {
    final var physicalTenantId = physicalTenantIdSupplier.get();
    final var reader = readersByPhysicalTenant.get(physicalTenantId);
    if (reader == null) {
      throw new IllegalStateException(
          "No AuthorizationReader registered for physical tenant '" + physicalTenantId + "'");
    }
    return reader;
  }
}
