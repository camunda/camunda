/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AuthorizationServices
    extends SearchQueryService<AuthorizationServices, AuthorizationQuery, AuthorizationEntity> {

  private final AuthorizationSearchClient authorizationSearchClient;
  private final SecurityConfiguration securityConfiguration;

  public AuthorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final Authentication authentication,
      final SecurityConfiguration securityConfiguration) {
    super(brokerClient, securityContextProvider, authentication);
    this.authorizationSearchClient = authorizationSearchClient;
    this.securityConfiguration = securityConfiguration;
  }

  @Override
  public AuthorizationServices withAuthentication(final Authentication authentication) {
    return new AuthorizationServices(
        brokerClient,
        securityContextProvider,
        authorizationSearchClient,
        authentication,
        securityConfiguration);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return authorizationSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.authorization().read())))
        .searchAuthorizations(query);
  }

  public List<AuthorizationEntity> findAll(final AuthorizationQuery query) {
    return authorizationSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.authorization().read())))
        .findAllAuthorizations(query);
  }

  public List<String> getAuthorizedResources(
      final Set<String> ownerIds,
      final PermissionType permissionType,
      final AuthorizationResourceType resourceType) {
    final var authorizationQuery =
        SearchQueryBuilders.authorizationSearchQuery(
            fn ->
                fn.filter(
                    f ->
                        f.ownerIds(ownerIds.stream().toList())
                            .permissionTypes(permissionType)
                            .resourceType(resourceType.name())));
    return findAll(authorizationQuery).stream().map(AuthorizationEntity::resourceId).toList();
  }

  public List<String> getAuthorizedApplications(final Set<String> ownerIds) {
    if (!securityConfiguration.getAuthorizations().isEnabled()) {
      // if authorizations are not enabled, we default to a wildcard authorization which is
      // needed for frontend side checks
      return List.of("*");
    }
    return getAuthorizedResources(
        ownerIds, PermissionType.ACCESS, AuthorizationResourceType.APPLICATION);
  }

  public Set<String> fetchAssignedPermissions(
      final String ownerId, final AuthorizationResourceType resourceType, final String resourceId) {
    final SearchQueryResult<AuthorizationEntity> result =
        search(
            SearchQueryBuilders.authorizationSearchQuery(
                fn ->
                    fn.filter(
                            f ->
                                f.resourceType(resourceType.name())
                                    .resourceIds(
                                        resourceId != null && !resourceId.isEmpty()
                                            ? resourceId
                                            : null)
                                    .ownerIds(ownerId))
                        .page(p -> p.size(1))));
    // TODO logic to fetch indirect authorizations via roles/groups should be added later
    return result.items().stream()
        .filter(authorization -> authorization.resourceId().contains(resourceId))
        .flatMap(authorization -> authorization.permissionTypes().stream())
        .map(PermissionType::name)
        .collect(Collectors.toSet());
  }

  public CompletableFuture<AuthorizationRecord> createAuthorization(
      final CreateAuthorizationRequest request) {
    final var brokerRequest =
        new BrokerAuthorizationRequest(AuthorizationIntent.CREATE)
            .setOwnerId(request.ownerId())
            .setOwnerType(request.ownerType())
            .setResourceType(request.resourceType())
            .setResourceId(request.resourceId())
            .setPermissionTypes(request.permissionTypes());
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<AuthorizationRecord> deleteAuthorization(final long authorizationKey) {
    final var brokerRequest = new BrokerAuthorizationDeleteRequest(authorizationKey);
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<AuthorizationRecord> updateAuthorization(
      final UpdateAuthorizationRequest request) {
    final var brokerRequest =
        new BrokerAuthorizationRequest(AuthorizationIntent.UPDATE)
            .setAuthorizationKey(request.authorizationKey())
            .setOwnerId(request.ownerId())
            .setOwnerType(request.ownerType())
            .setResourceId(request.resourceId())
            .setResourceType(request.resourceType())
            .setPermissionTypes(request.permissionTypes());
    return sendBrokerRequest(brokerRequest);
  }

  public record CreateAuthorizationRequest(
      String ownerId,
      AuthorizationOwnerType ownerType,
      String resourceId,
      AuthorizationResourceType resourceType,
      Set<PermissionType> permissionTypes) {}

  public record UpdateAuthorizationRequest(
      long authorizationKey,
      String ownerId,
      AuthorizationOwnerType ownerType,
      String resourceId,
      AuthorizationResourceType resourceType,
      Set<PermissionType> permissionTypes) {}
}
