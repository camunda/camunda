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
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
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
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
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
      final CamundaAuthentication authentication,
      final SecurityConfiguration securityConfiguration) {
    super(brokerClient, securityContextProvider, authentication);
    this.authorizationSearchClient = authorizationSearchClient;
    this.securityConfiguration = securityConfiguration;
  }

  @Override
  public AuthorizationServices withAuthentication(final CamundaAuthentication authentication) {
    return new AuthorizationServices(
        brokerClient,
        securityContextProvider,
        authorizationSearchClient,
        authentication,
        securityConfiguration);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return executeSearchRequest(
        () ->
            authorizationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, Authorization.of(a -> a.authorization().read())))
                .searchAuthorizations(query));
  }

  public List<String> getAuthorizedApplications(
      final Map<EntityType, Set<String>> ownerTypeToOwnerIds) {
    if (!securityConfiguration.getAuthorizations().isEnabled()) {
      // if authorizations are not enabled, we default to a wildcard authorization which is
      // needed for frontend side checks
      return List.of("*");
    }

    if (ownerTypeToOwnerIds == null || ownerTypeToOwnerIds.isEmpty()) {
      // if no ownerIds are provided, we return an empty list to be defensive, we can't work out
      // which applications the user has access to if we don't know the ownerIds
      return List.of();
    }

    return search(
            SearchQueryBuilders.authorizationSearchQuery(
                fn ->
                    fn.filter(
                            f ->
                                f.resourceType(AuthorizationResourceType.APPLICATION.name())
                                    .permissionTypes(PermissionType.ACCESS)
                                    .ownerTypeToOwnerIds(ownerTypeToOwnerIds))
                        .page(p -> p.size(1))))
        .items()
        .stream()
        .map(AuthorizationEntity::resourceId)
        .collect(Collectors.toList());
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

  public AuthorizationEntity getAuthorization(final long authorizationKey) {
    return search(
            SearchQueryBuilders.authorizationSearchQuery()
                .filter(f -> f.authorizationKey(authorizationKey))
                .singleResult()
                .build())
        .items()
        .getFirst();
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
