/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerAuthorizationCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AuthorizationServices<T> extends ApiServices<AuthorizationServices<T>> {

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

  public CompletableFuture<AuthorizationRecord> createAuthorization(
      final String ownerKey,
      final AuthorizationOwnerType ownerType,
      final String resourceKey,
      final String resourceType,
      final List<String> permissions) {
    return sendBrokerRequest(
        new BrokerAuthorizationCreateRequest()
            .setOwnerKey(ownerKey)
            .setOwnerType(ownerType)
            .setResourceKey(resourceKey)
            .setResourceType(resourceType)
            .setPermissions(permissions));
  }
}
