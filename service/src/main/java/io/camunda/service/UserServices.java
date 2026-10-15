/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.USER_READ_AUTHORIZATION;

import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserServices extends SearchQueryService<UserServices, UserQuery, UserEntity> {

  private final UserSearchClient userSearchClient;
  private final PasswordEncoder passwordEncoder;

  public UserServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserSearchClient userSearchClient,
      final PasswordEncoder passwordEncoder,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.userSearchClient = userSearchClient;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public SearchQueryResult<UserEntity> search(
      final UserQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            userSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, USER_READ_AUTHORIZATION))
                .searchUsers(query));
  }

  public CompletableFuture<UserRecord> createUser(
      final UserDTO request, final CamundaAuthentication authentication) {
    final String encodedPassword = passwordEncoder.encode(request.password());
    return sendBrokerRequest(
        new BrokerUserCreateRequest()
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword)
            .setExternalId(request.externalId())
            .setActive(request.active())
            .setAttributes(request.attributes()),
        authentication);
  }

  public CompletableFuture<UserRecord> createInitialAdminUser(
      final UserDTO request, final CamundaAuthentication authentication) {
    final String encodedPassword = passwordEncoder.encode(request.password());
    return sendBrokerRequest(
        new BrokerUserCreateRequest(UserIntent.CREATE_INITIAL_ADMIN)
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword),
        authentication);
  }

  public UserEntity getUser(final String username, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            userSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(USER_READ_AUTHORIZATION, username)))
                .getUser(username));
  }

  public CompletableFuture<UserRecord> updateUser(
      final UserDTO request, final CamundaAuthentication authentication) {
    final String encodedPassword =
        StringUtils.isEmpty(request.password) ? "" : passwordEncoder.encode(request.password());
    return sendBrokerRequest(
        new BrokerUserUpdateRequest()
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword),
        authentication);
  }

  /**
   * Updates only the given attributes of the user; all other attributes keep their current value.
   */
  public CompletableFuture<UserRecord> updateUser(
      final UserDTO request,
      final Set<UserAttribute> changedAttributes,
      final CamundaAuthentication authentication) {
    final String encodedPassword =
        changedAttributes.contains(UserAttribute.PASSWORD) && !StringUtils.isEmpty(request.password)
            ? passwordEncoder.encode(request.password())
            : "";
    return sendBrokerRequest(
        new BrokerUserUpdateRequest()
            .setUsername(request.username())
            .setName(request.name())
            .setEmail(request.email())
            .setPassword(encodedPassword)
            .setExternalId(request.externalId())
            .setActive(request.active())
            .setAttributes(request.attributes())
            .setChangedAttributes(
                changedAttributes.stream()
                    .map(attribute -> UserRecord.UserAttribute.valueOf(attribute.name()))
                    .toList()),
        authentication);
  }

  public CompletableFuture<UserRecord> deleteUser(
      final String username, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerUserDeleteRequest().setUsername(username), authentication);
  }

  public enum UserAttribute {
    NAME,
    EMAIL,
    PASSWORD,
    EXTERNAL_ID,
    ACTIVE,
    ATTRIBUTES,
    ATTRIBUTES_MERGE
  }

  public record UserDTO(
      String username,
      String name,
      String email,
      String password,
      String externalId,
      boolean active,
      Map<String, Object> attributes) {

    public UserDTO(
        final String username,
        final String name,
        final String email,
        final String password,
        final String externalId,
        final boolean active) {
      this(username, name, email, password, externalId, active, Map.of());
    }

    public UserDTO(
        final String username, final String name, final String email, final String password) {
      this(username, name, email, password, "", true, Map.of());
    }
  }
}
