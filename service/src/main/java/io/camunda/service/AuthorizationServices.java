/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.exception.SearchQueryExecutionException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.AuthorizationQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationPatchRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AuthorizationServices<T>
    extends SearchQueryService<AuthorizationServices<T>, AuthorizationQuery, AuthorizationEntity> {

  private final AuthorizationSearchClient authorizationSearchClient;

  public AuthorizationServices(
      final BrokerClient brokerClient,
      final AuthorizationSearchClient authorizationSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
   this.authorizationSearchClient = authorizationSearchClient;
  }

  @Override
  public AuthorizationServices<T> withAuthentication(final Authentication authentication) {
    return new AuthorizationServices<>(brokerClient, this.authorizationSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return authorizationSearchClient
        .searchAuthorizations(query, authentication)
        .fold(
            (e) -> {
              throw new SearchQueryExecutionException("Failed to execute search query", e);
            },
            (r) -> r);
  }

  public CompletableFuture<AuthorizationRecord> patchAuthorization(
      final PatchAuthorizationRequest request) {
    final var brokerRequest =
        new BrokerAuthorizationPatchRequest()
            .setOwnerKey(request.ownerKey())
            .setAction(request.action())
            .setResourceType(request.resourceType());
    request.permissions().forEach(brokerRequest::addPermissions);
    return sendBrokerRequest(brokerRequest);
  }

  public record PatchAuthorizationRequest(
      long ownerKey,
      PermissionAction action,
      AuthorizationResourceType resourceType,
      Map<PermissionType, List<String>> permissions) {}
}
