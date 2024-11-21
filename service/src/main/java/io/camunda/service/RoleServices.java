/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.roleSearchQuery;

import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleUpdateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleDeleteRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import java.util.concurrent.CompletableFuture;

public class RoleServices extends SearchQueryService<RoleServices, RoleQuery, RoleEntity> {

  private final RoleSearchClient roleSearchClient;

  public RoleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.roleSearchClient = roleSearchClient;
  }

  @Override
  public SearchQueryResult<RoleEntity> search(final RoleQuery query) {
    return roleSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.role().read())))
        .searchRoles(query);
  }

  @Override
  public RoleServices withAuthentication(final Authentication authentication) {
    return new RoleServices(
        brokerClient, securityContextProvider, roleSearchClient, authentication);
  }

  public CompletableFuture<RoleRecord> createRole(final String name) {
    return sendBrokerRequest(new BrokerRoleCreateRequest().setName(name));
  }

  public CompletableFuture<RoleRecord> updateRole(final long roleKey, final String name) {
    return sendBrokerRequest(new BrokerRoleUpdateRequest(roleKey).setName(name));
  }

  public RoleEntity getRole(final Long roleKey) {
    final var result =
        roleSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchRoles(roleSearchQuery(q -> q.filter(f -> f.roleKey(roleKey))));
    final var roleEntity = getSingleResultOrThrow(result, roleKey, "Role");
    final var authorization = Authorization.of(a -> a.role().read());
    if (!securityContextProvider.isAuthorized(roleEntity.name(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return roleEntity;
  }

  public CompletableFuture<RoleRecord> deleteRole(final long roleKey) {
    return sendBrokerRequest(new BrokerRoleDeleteRequest(roleKey));
  }
}
