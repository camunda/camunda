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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.concurrent.CompletableFuture;

public class UserServices<T> extends ApiServices<UserServices<T>> {

  public UserServices(final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public UserServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public UserServices<T> withAuthentication(final Authentication authentication) {
    return new UserServices<>(brokerClient, searchClient, transformers, authentication);
  }

  public CompletableFuture<UserRecord> createUser(
      final String username, final String name, final String email, final String password) {
    return sendBrokerRequest(
        new BrokerUserCreateRequest()
            .setUsername(username)
            .setName(name)
            .setEmail(email)
            .setPassword(password));
  }
}
