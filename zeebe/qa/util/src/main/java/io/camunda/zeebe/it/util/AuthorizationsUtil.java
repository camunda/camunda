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
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.client.wrappers.OwnerType;
import io.camunda.client.wrappers.ResourceType;
import io.camunda.search.clients.SearchClients;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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
  private final SearchClients searchClients;

  public AuthorizationsUtil(
      final TestGateway<?> gateway, final CamundaClient client, final String elasticsearchUrl) {
    this(gateway, client, SearchClientsUtil.createSearchClients(elasticsearchUrl));
  }

  public AuthorizationsUtil(
      final TestGateway<?> gateway, final CamundaClient client, final SearchClients searchClients) {
    this.gateway = gateway;
    this.client = client;
    this.searchClients = searchClients;
  }

  public static AuthorizationsUtil create(
      final TestGateway<?> gateway, final String elasticsearchUrl) {
    final var authorizationUtil =
        new AuthorizationsUtil(
            gateway,
            createClient(gateway, DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD),
            SearchClientsUtil.createSearchClients(elasticsearchUrl));
    authorizationUtil.awaitUserExistsInElasticsearch(DEFAULT_USER_USERNAME);
    return authorizationUtil;
  }

  public long createUser(final String username, final String password) {
    return createUserWithPermissions(username, password);
  }

  public long createUserWithPermissions(
      final String username, final String password, final Permissions... permissions) {
    final var userCreateResponse =
        client
            .newUserCreateCommand()
            .username(username)
            .password(password)
            .name("name")
            .email("foo@bar.com")
            .send()
            .join();
    awaitUserExistsInElasticsearch(username);
    createPermissions(username, permissions);
    return userCreateResponse.getUserKey();
  }

  public void createPermissions(final String username, final Permissions... permissions) {
    for (final Permissions permission : permissions) {
      for (final String resourceId : permission.resourceIds()) {
        client
            .newCreateAuthorizationCommand()
            .ownerId(username)
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(ResourceType.fromProtocolEnum(permission.resourceType()))
            .permissionTypes(
                io.camunda.client.wrappers.PermissionType.fromProtocolEnum(
                    permission.permissionType()))
            .send()
            .join();
      }
    }
    if (permissions.length > 0) {
      awaitPermissionExistsInElasticsearch(username, Arrays.asList(permissions).getLast());
    }
  }

  public void createTenant(
      final String tenantId, final String tenantName, final String... usernames) {
    client
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name(tenantName)
        .send()
        .join()
        .getTenantKey();
    for (final var username : usernames) {
      client.newAssignUserToTenantCommand(tenantId).username(username).send().join();
    }
    awaitTenantExistsInElasticsearch(tenantId);
  }

  public CamundaClient createClient(final String username, final String password) {
    return createClient(gateway, username, password);
  }

  public CamundaClient createClientGrpc(final String username, final String password) {
    return createClientGrpc(gateway, username, password);
  }

  public CamundaClient createUserAndClient(
      final String username, final String password, final Permissions... permissions) {
    createUserWithPermissions(username, password, permissions);
    return createClient(gateway, username, password);
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

  private void awaitTenantExistsInElasticsearch(final String tenantId) {
    final var tenantQuery = TenantQuery.of(b -> b.filter(f -> f.tenantId(tenantId)));
    awaitEntityExistsInElasticsearch(() -> searchClients.searchTenants(tenantQuery));
  }

  public void awaitUserExistsInElasticsearch(final String username) {
    final var userQuery = UserQuery.of(b -> b.filter(f -> f.username(username)));
    awaitEntityExistsInElasticsearch(() -> searchClients.searchUsers(userQuery));
  }

  private void awaitPermissionExistsInElasticsearch(
      final String username, final Permissions permissions) {
    final var resourceType = permissions.resourceType().getValue();
    final var permissionType = PermissionType.valueOf(permissions.permissionType().getValue());
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
                              .resourceType(resourceType)
                              .permissionTypes(permissionType)
                              .resourceIds(resourceId)));

      awaitEntityExistsInElasticsearch(() -> searchClients.searchAuthorizations(permissionQuery));
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
    searchClients.close();
  }

  public record Permissions(
      ResourceTypeEnum resourceType, PermissionTypeEnum permissionType, List<String> resourceIds) {}
}
