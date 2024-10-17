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
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationPatchRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    return new AuthorizationServices<>(brokerClient, authorizationSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return authorizationSearchClient.searchAuthorizations(query, authentication);
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
                                    .resourceKey(
                                        resourceId != null && !resourceId.isEmpty()
                                            ? resourceId
                                            : null)
                                    .ownerKey(ownerId))
                        .page(p -> p.size(1))));
    // TODO logic to fetch indirect authorizations via roles/groups should be added later
    return result.items().stream()
        .flatMap(a -> a.value().permissions().stream())
        .collect(Collectors.toSet());
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
