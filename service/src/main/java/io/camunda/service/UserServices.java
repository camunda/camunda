/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.USER_READ_AUTHORIZATION;

import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserServices extends SearchQueryService<UserServices, UserQuery, UserEntity> {

  private final UserSearchClient userSearchClient;
  private final PasswordEncoder passwordEncoder;

  public UserServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserSearchClient userSearchClient,
      final CamundaAuthentication authentication,
      final PasswordEncoder passwordEncoder,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.userSearchClient = userSearchClient;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public SearchQueryResult<UserEntity> search(final UserQuery query) {
    return executeSearchRequest(
        () ->
            userSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, USER_READ_AUTHORIZATION))
                .searchUsers(query));
  }

  @Override
  public UserServices withAuthentication(final CamundaAuthentication authentication) {
    return new UserServices(
        brokerClient,
        securityContextProvider,
        userSearchClient,
        authentication,
        passwordEncoder,
        executorProvider);
  }

  public CompletableFuture<UserRecord> createUser(final UserDTO request) {
    final String encodedPassword = passwordEncoder.encode(request.password());
    return sendBrokerRequest(
        new BrokerUserCreateRequest()
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword));
  }

  public CompletableFuture<UserRecord> createInitialAdminUser(final UserDTO request) {
    final String encodedPassword = passwordEncoder.encode(request.password());
    return sendBrokerRequest(
        new BrokerUserCreateRequest(UserIntent.CREATE_INITIAL_ADMIN)
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword));
  }

  public UserEntity getUser(final String username) {
    return executeSearchRequest(
        () ->
            userSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, withAuthorization(USER_READ_AUTHORIZATION, username)))
                .getUser(username));
  }

  public CompletableFuture<UserRecord> updateUser(final UserDTO request) {
    final String encodedPassword =
        StringUtils.isEmpty(request.password) ? "" : passwordEncoder.encode(request.password());
    return sendBrokerRequest(
        new BrokerUserUpdateRequest()
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword));
  }

  public CompletableFuture<UserRecord> deleteUser(final String username) {
    return sendBrokerRequest(new BrokerUserDeleteRequest().setUsername(username));
  }

  public record UserDTO(String username, String name, String email, String password) {}
}
