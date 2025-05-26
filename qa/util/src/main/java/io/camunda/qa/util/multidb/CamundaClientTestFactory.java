/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamundaClientTestFactory implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaClientTestFactory.class);

  private final Map<String, CamundaClient> cachedClients = new ConcurrentHashMap<>();

  public CamundaClientTestFactory withUsers(
      final TestStandaloneApplication<?> application, final List<User> users) {
    final CamundaClient defaultClient =
        cachedClients.computeIfAbsent(
            InitializationConfiguration.DEFAULT_USER_USERNAME,
            __ -> createDefaultUserClient(application));
    users.forEach(
        user -> {
          createUserWithPermissions(
              defaultClient, user.username(), user.password(), user.permissions());
          createClientForUser(application, user);
        });
    return this;
  }

  public CamundaClient getCamundaClient(
      final TestGateway<?> gateway, final Authenticated authenticated) {
    if (authenticated == null) {
      LOGGER.info(
          "Creating unauthorized Camunda client for broker address '{}", gateway.restAddress());
      return gateway.newClientBuilder().preferRestOverGrpc(true).build();
    }

    LOGGER.info(
        "Retrieving Camunda client for user '{}' and broker address '{}",
        authenticated.value(),
        gateway.restAddress());
    final var username = authenticated.value();
    return cachedClients.get(username);
  }

  private void createClientForUser(final TestGateway<?> gateway, final User user) {
    final var client = createAuthenticatedClient(gateway, user.username(), user.password());
    cachedClients.put(user.username(), client);
  }

  private CamundaClient createDefaultUserClient(final TestGateway<?> gateway) {
    final CamundaClient defaultClient =
        createAuthenticatedClient(
            gateway,
            InitializationConfiguration.DEFAULT_USER_USERNAME,
            InitializationConfiguration.DEFAULT_USER_PASSWORD);
    // block until the default user is created
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(defaultClient.newTopologyRequest().send().join())
                    .isHealthy());
    return defaultClient;
  }

  private void createUserWithPermissions(
      final CamundaClient defaultClient,
      final String username,
      final String password,
      final List<Permissions> permissions) {
    defaultClient
        .newUserCreateCommand()
        .username(username)
        .password(password)
        .name(username)
        .email("%s@foo.com".formatted(username))
        .send()
        .join();

    permissions.forEach(
        permission -> {
          permission
              .resourceIds()
              .forEach(
                  resourceId -> {
                    defaultClient
                        .newCreateAuthorizationCommand()
                        .ownerId(username)
                        .ownerType(OwnerType.USER)
                        .resourceId(resourceId)
                        .resourceType(permission.resourceType())
                        .permissionTypes(permission.permissionType())
                        .send()
                        .join();
                  });
        });
    // TODO replace with proper user get by key once it is implemented
    try {
      Thread.sleep(2000);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private CamundaClient createAuthenticatedClient(
      final TestGateway<?> gateway, final String username, final String password) {
    return gateway
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder().username(username).password(password).build())
        .build();
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(cachedClients.values());
  }
}
