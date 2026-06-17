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
 * SPIKE (ADR-0005): the <b>control-plane</b> physical-tenant-routing {@link AuthorizationReader}.
 *
 * <p>Resolves {@link PhysicalTenantContext#current()} on each call and delegates to that tenant's
 * {@link AuthorizationReader}, so a control-plane permission check reads the in-context tenant's
 * authorization data instead of a single {@code default}-pinned reader (#55252). It is the
 * <b>control-plane</b> mechanism of ADR-0005's two-mechanism decision: the reader behind the
 * OC-supplied {@code AuthorizationRepositoryPort} adapter ({@code AuthorizationRepositoryAdapter})
 * used by Spring Security's permission checks.
 *
 * <p>The control-plane runs exclusively on the request thread — the engine never invokes {@code
 * hasPermission} — where the pre-security filter (ADR-0003) has already stamped the physical tenant.
 * Thread-bound resolution via {@link PhysicalTenantContext#current()} is therefore safe here,
 * including in a standalone-gateway deployment (the permission check only ever runs on the gateway's
 * request thread). {@code current()} falls back to {@code default} for non-prefixed cluster requests.
 *
 * <p><b>The data-plane does NOT rely on thread-local resolution.</b> Result authorization inside
 * {@code CamundaSearchClients} is <em>instance-bound</em>: {@code withPhysicalTenant(pt)} selects
 * that tenant's {@code ResourceAccessController} (built over the tenant's {@link AuthorizationReader}),
 * so it stays correct even when the search runs off the request thread (e.g. batch operations in the
 * engine), where no {@link PhysicalTenantContext} is bound. See ADR-0005's two-mechanism decision.
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
