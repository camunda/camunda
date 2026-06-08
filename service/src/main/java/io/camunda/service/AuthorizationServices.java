/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.AUTHORIZATION_READ_AUTHORIZATION;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class AuthorizationServices
    extends SearchQueryService<AuthorizationServices, AuthorizationQuery, AuthorizationEntity> {

  private final AuthorizationSearchClient authorizationSearchClient;

  public AuthorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.authorizationSearchClient = authorizationSearchClient;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(
      final AuthorizationQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            authorizationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AUTHORIZATION_READ_AUTHORIZATION))
                .searchAuthorizations(query));
  }

  public CompletableFuture<AuthorizationRecord> createAuthorization(
      final CreateAuthorizationRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerAuthorizationRequest(AuthorizationIntent.CREATE)
            .setOwnerId(request.ownerId())
            .setOwnerType(AuthzModelMapper.toProtocol(request.ownerType()))
            .setResourceType(AuthzModelMapper.toProtocol(request.resourceType()))
            .setResourceMatcher(AuthzModelMapper.toProtocol(request.resourceMatcher()))
            .setResourceId(request.resourceId())
            .setResourcePropertyName(request.resourcePropertyName())
            .setPermissionTypes(
                AuthzModelMapper.toProtocolPermissionTypes(request.permissionTypes()));
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public AuthorizationEntity getAuthorization(
      final long authorizationKey, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            authorizationSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            AUTHORIZATION_READ_AUTHORIZATION, String.valueOf(authorizationKey))))
                .getAuthorization(authorizationKey));
  }

  public CompletableFuture<AuthorizationRecord> deleteAuthorization(
      final long authorizationKey, final CamundaAuthentication authentication) {
    final var brokerRequest = new BrokerAuthorizationDeleteRequest(authorizationKey);
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<AuthorizationRecord> updateAuthorization(
      final UpdateAuthorizationRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerAuthorizationRequest(AuthorizationIntent.UPDATE)
            .setAuthorizationKey(request.authorizationKey())
            .setOwnerId(request.ownerId())
            .setOwnerType(AuthzModelMapper.toProtocol(request.ownerType()))
            .setResourceMatcher(AuthzModelMapper.toProtocol(request.resourceMatcher()))
            .setResourceId(request.resourceId())
            .setResourcePropertyName(request.resourcePropertyName())
            .setResourceType(AuthzModelMapper.toProtocol(request.resourceType()))
            .setPermissionTypes(
                AuthzModelMapper.toProtocolPermissionTypes(request.permissionTypes()));
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public record CreateAuthorizationRequest(
      String ownerId,
      AuthorizationOwnerType ownerType,
      AuthorizationResourceMatcher resourceMatcher,
      String resourceId,
      String resourcePropertyName,
      AuthorizationResourceType resourceType,
      Set<PermissionType> permissionTypes) {}

  public record UpdateAuthorizationRequest(
      long authorizationKey,
      String ownerId,
      AuthorizationOwnerType ownerType,
      AuthorizationResourceMatcher resourceMatcher,
      String resourceId,
      String resourcePropertyName,
      AuthorizationResourceType resourceType,
      Set<PermissionType> permissionTypes) {}
}
