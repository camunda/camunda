/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TenantServices extends SearchQueryService<TenantServices, TenantQuery, TenantEntity> {

  private final TenantSearchClient tenantSearchClient;

  public TenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.tenantSearchClient = tenantSearchClient;
  }

  @Override
  public SearchQueryResult<TenantEntity> search(final TenantQuery query) {
    return tenantSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.tenant().read())))
        .searchTenants(query);
  }

  @Override
  public TenantServices withAuthentication(final Authentication authentication) {
    return new TenantServices(
        brokerClient, securityContextProvider, tenantSearchClient, authentication);
  }

  public CompletableFuture<TenantRecord> createTenant(final TenantDTO request) {
    return sendBrokerRequest(
        new BrokerTenantCreateRequest().setTenantId(request.tenantId()).setName(request.name()));
  }

  public CompletableFuture<TenantRecord> updateTenant(final TenantDTO request) {
    return sendBrokerRequest(new BrokerTenantUpdateRequest(request.key()).setName(request.name()));
  }

  public CompletableFuture<TenantRecord> deleteTenant(final long key) {
    return sendBrokerRequest(new BrokerTenantDeleteRequest(key).setTenantKey(key));
  }

  public TenantEntity getTenant(final Long tenantKey) {
    return findTenant(tenantKey)
        .orElseThrow(
            () -> new NotFoundException("Tenant with key %d not found".formatted(tenantKey)));
  }

  public Optional<TenantEntity> findTenant(final Long tenantKey) {
    return search(SearchQueryBuilders.tenantSearchQuery().filter(f -> f.key(tenantKey)).build())
        .items()
        .stream()
        .findFirst();
  }

  public record TenantDTO(Long key, String tenantId, String name) {}
}
