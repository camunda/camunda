/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.awaitility.Awaitility;

public class AuthorizationsUtil implements CloseableSilently {

  private final TestGateway<?> gateway;
  private final CamundaClient client;
  private final DocumentBasedSearchClient documentBasedSearchClient;
  private final AuthorizationReader authorizationReader;
  private final UserReader userReader;

  public AuthorizationsUtil(
      final TestGateway<?> gateway, final CamundaClient client, final String elasticsearchUrl) {
    this(gateway, client, SearchClientsUtil.createLowLevelSearchClient(elasticsearchUrl));
  }

  public AuthorizationsUtil(
      final TestGateway<?> gateway,
      final CamundaClient client,
      final DocumentBasedSearchClient documentBasedSearchClient) {
    this.gateway = gateway;
    this.client = client;
    this.documentBasedSearchClient = documentBasedSearchClient;
    authorizationReader = SearchClientsUtil.createAuthorizationReader(documentBasedSearchClient);
    userReader = SearchClientsUtil.createUserReader(documentBasedSearchClient);
  }

  public static AuthorizationsUtil create(
      final TestGateway<?> gateway, final String elasticsearchUrl) {
    final var authorizationUtil =
        new AuthorizationsUtil(
            gateway,
            createClient(gateway, DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD),
            SearchClientsUtil.createLowLevelSearchClient(elasticsearchUrl));
    authorizationUtil.awaitUserExistsInElasticsearch(DEFAULT_USER_USERNAME);
    return authorizationUtil;
  }

  public void createUser(final String username, final String password) {
    createUserWithPermissions(username, password);
  }

  public void createUserWithPermissions(
      final String username, final String password, final Permissions... permissions) {
    client
        .newCreateUserCommand()
        .username(username)
        .password(password)
        .name("name")
        .email("foo@bar.com")
        .send()
        .join();
    awaitUserExistsInElasticsearch(username);
    createPermissions(username, permissions);
  }

  public void createPermissions(final String username, final Permissions... permissions) {
    for (final Permissions permission : permissions) {
      for (final String resourceId : permission.resourceIds()) {
        client
            .newCreateAuthorizationCommand()
            .ownerId(username)
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(EnumUtil.convert(permission.resourceType(), ResourceType.class))
            .permissionTypes(permission.permissionType())
            .send()
            .join();
      }
    }
    if (permissions.length > 0) {
      awaitPermissionExistsInElasticsearch(username, Arrays.asList(permissions).getLast());
    }
  }

  public CamundaClient createClient(final String username, final String password) {
    return createClient(gateway, username, password);
  }

  public CamundaClient createClientGrpc(final String username, final String password) {
    return createClientGrpc(gateway, username, password);
  }

  public static CamundaClient createClient(
      final TestGateway<?> gateway, final String username, final String password) {
    return gateway
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder().username(username).password(password).build())
        .build();
  }

  public static CamundaClient createClientGrpc(
      final TestGateway<?> gateway, final String username, final String password) {
    return gateway
        .newClientBuilder()
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .preferRestOverGrpc(false)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder().username(username).password(password).build())
        .build();
  }

  public void awaitUserExistsInElasticsearch(final String username) {
    final var userQuery = UserQuery.of(b -> b.filter(f -> f.usernames(username)));
    awaitEntityExistsInElasticsearch(
        () -> userReader.search(userQuery, ResourceAccessChecks.disabled()));
  }

  private void awaitPermissionExistsInElasticsearch(
      final String username, final Permissions permissions) {
    final var resourceType = permissions.resourceType();
    final var permissionType = permissions.permissionType();
    final var resourceIds = permissions.resourceIds();
    // since the resourceIds filter uses an OR condition, we need to wait for the exported
    // authorization to contain all resourceIds
    for (final String resourceId : resourceIds) {
      final var permissionQuery =
          AuthorizationQuery.of(
              b ->
                  b.filter(
                      f ->
                          f.ownerIds(username)
                              .resourceType(resourceType.name())
                              .permissionTypes(
                                  io.camunda.zeebe.protocol.record.value.PermissionType.valueOf(
                                      permissionType.name()))
                              .resourceIds(resourceId)));

      awaitEntityExistsInElasticsearch(
          () -> authorizationReader.search(permissionQuery, ResourceAccessChecks.disabled()));
    }
  }

  private void awaitEntityExistsInElasticsearch(
      final Supplier<SearchQueryResult<?>> searchQueryResultSupplier) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .until(() -> searchQueryResultSupplier.get().total() > 0);
  }

  public CamundaClient getDefaultClient() {
    return client;
  }

  @Override
  public void close() {
    client.close();
    documentBasedSearchClient.close();
  }

  public record Permissions(
      ResourceType resourceType, PermissionType permissionType, List<String> resourceIds) {}
}
