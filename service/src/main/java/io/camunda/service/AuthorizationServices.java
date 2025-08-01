/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.AUTHORIZATION_READ_AUTHORIZATION;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class AuthorizationServices
    extends SearchQueryService<AuthorizationServices, AuthorizationQuery, AuthorizationEntity> {

  private final AuthorizationSearchClient authorizationSearchClient;

  public AuthorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.authorizationSearchClient = authorizationSearchClient;
  }

  @Override
  public AuthorizationServices withAuthentication(final CamundaAuthentication authentication) {
    return new AuthorizationServices(
        brokerClient, securityContextProvider, authorizationSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return executeSearchRequest(
        () ->
            authorizationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AUTHORIZATION_READ_AUTHORIZATION))
                .searchAuthorizations(query));
  }

  public CompletableFuture<AuthorizationRecord> createAuthorization(
      final CreateAuthorizationRequest request) {
    final var brokerRequest =
        new BrokerAuthorizationRequest(AuthorizationIntent.CREATE)
            .setOwnerId(request.ownerId())
            .setOwnerType(request.ownerType())
            .setResourceType(request.resourceType())
            .setResourceMatcher(getResourceMatcher(request.resourceId()))
            .setResourceId(request.resourceId())
            .setPermissionTypes(request.permissionTypes());
    return sendBrokerRequest(brokerRequest);
  }

  public AuthorizationEntity getAuthorization(final long authorizationKey) {
    return executeSearchRequest(
        () ->
            authorizationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            AUTHORIZATION_READ_AUTHORIZATION, String.valueOf(authorizationKey))))
                .getAuthorization(authorizationKey));
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
            .setResourceMatcher(getResourceMatcher(request.resourceId()))
            .setResourceId(request.resourceId())
            .setResourceType(request.resourceType())
            .setPermissionTypes(request.permissionTypes());
    return sendBrokerRequest(brokerRequest);
  }

  private AuthorizationResourceMatcher getResourceMatcher(final String resourceId) {
    // TODO: use WILDCARD constant or find another place to set the matcher
    return "*".equals(resourceId)
        ? AuthorizationResourceMatcher.ANY
        : AuthorizationResourceMatcher.ID;
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
