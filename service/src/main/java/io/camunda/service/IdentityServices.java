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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IdentityServices<T> extends ApiServices<IdentityServices<T>> {

  public IdentityServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public IdentityServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public IdentityServices<T> withAuthentication(final Authentication authentication) {
    return new IdentityServices<>(brokerClient, searchClient, transformers, authentication);
  }

  public CompletableFuture<UserRecord> createUser(
      final String username, final String name, final String email) {
    return sendBrokerRequest(
        new BrokerUserCreateRequest().setUsername(username).setName(name).setEmail(email));
  }

  public CompletableFuture<AuthorizationRecord> createAuthorization(
      final String username,
      final String resourceKey,
      final String resourceType,
      final List<String> permissions) {
    return sendBrokerRequest(
        new BrokerAuthorizationCreateRequest()
            .setUsername(username)
            .setResourceKey(resourceKey)
            .setResourceType(resourceType)
            .setPermissions(permissions));
  }
}
