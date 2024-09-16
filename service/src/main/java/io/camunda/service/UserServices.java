/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.UserSearchClient;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.exception.SearchQueryExecutionException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.concurrent.CompletableFuture;

public class UserServices extends SearchQueryService<UserServices, UserQuery, UserEntity> {

  private final UserSearchClient userSearchClient;

  public UserServices(
      final BrokerClient brokerClient,
      final UserSearchClient userSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.userSearchClient = userSearchClient;
  }

  @Override
  public SearchQueryResult<UserEntity> search(final UserQuery query) {
    return userSearchClient
        .searchUsers(query, authentication)
        .fold(
            (e) -> {
              throw new SearchQueryExecutionException("Failed to execute search query", e);
            },
            (r) -> r);
  }

  @Override
  public UserServices withAuthentication(final Authentication authentication) {
    return new UserServices(brokerClient, this.userSearchClient, authentication);
  }

  public CompletableFuture<UserRecord> createUser(final CreateUserRequest request) {
    return sendBrokerRequest(
        new BrokerUserCreateRequest()
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(request.password()));
  }

  public record CreateUserRequest(String username, String name, String email, String password) {}
}
