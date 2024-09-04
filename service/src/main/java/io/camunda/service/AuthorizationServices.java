/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.AuthorizationQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AuthorizationServices<T>
    extends SearchQueryService<AuthorizationServices<T>, AuthorizationQuery, AuthorizationEntity> {

  public AuthorizationServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public AuthorizationServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public AuthorizationServices<T> withAuthentication(final Authentication authentication) {
    return new AuthorizationServices<>(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return executor.search(query, AuthorizationEntity.class);
  }

  public CompletableFuture<AuthorizationRecord> createAuthorization(
      final Long ownerKey,
      final AuthorizationOwnerType ownerType,
      final String resourceType,
      final List<String> resourceIds) {
    return sendBrokerRequest(
        new BrokerAuthorizationCreateRequest()
            .setOwnerKey(ownerKey)
            .setOwnerType(ownerType)
            .setResourceType(resourceType)
            .addPermissions(PermissionType.CREATE, resourceIds));
    // TODO set proper PermissionType. This requires changes in the REST API
  }
}
