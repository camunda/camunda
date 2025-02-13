/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProvider;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamundaClientTestFactory implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaClientTestFactory.class);

  private final Map<String, User> usersRegistry = new HashMap<>();
  private final Map<String, CamundaClient> cachedClients = new ConcurrentHashMap<>();

  public CamundaClientTestFactory() {
    usersRegistry.put(InitializationConfiguration.DEFAULT_USER_USERNAME, User.DEFAULT);
  }

  public CamundaClientTestFactory withUsers(final List<User> users) {
    users.forEach(user -> usersRegistry.put(user.username(), user));
    return this;
  }

  public CamundaClient createCamundaClient(
      final TestGateway<?> gateway, final Authenticated authenticated) {
    if (authenticated == null) {
      LOGGER.info(
          "Creating unauthorized Zeebe client for broker address '{}", gateway.restAddress());
      return gateway.newClientBuilder().build();
    } else {
      LOGGER.info(
          "Creating Zeebe client for user '{}' and broker address '{}",
          authenticated.value(),
          gateway.restAddress());
    }
    final CamundaClient defaultClient =
        cachedClients.computeIfAbsent(
            InitializationConfiguration.DEFAULT_USER_USERNAME,
            __ -> createDefaultUserClient(gateway));
    final String username = authenticated.value();
    if (InitializationConfiguration.DEFAULT_USER_USERNAME.equals(username)) {
      return defaultClient;
    } else {
      return cachedClients.computeIfAbsent(
          username, k -> createClientForUser(gateway, defaultClient, k));
    }
  }

  private CamundaClient createClientForUser(
      final TestGateway<?> gateway, final CamundaClient defaultClient, final String username) {
    final User user = usersRegistry.get(username);
    createUserWithPermissions(defaultClient, user.username(), user.password(), user.permissions());
    return createAuthenticatedClient(gateway, user.username(), user.password());
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
                        .ownerType(OwnerTypeEnum.USER)
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
        .credentialsProvider(new BasicAuthCredentialsProvider(username, password))
        .build();
  }

  @Override
  public void close() {
    cachedClients.values().forEach(CamundaClient::close);
  }

  public record Permissions(
      ResourceTypeEnum resourceType, PermissionTypeEnum permissionType, List<String> resourceIds) {}

  public record User(String username, String password, List<Permissions> permissions) {
    public static final User DEFAULT =
        new User(
            InitializationConfiguration.DEFAULT_USER_USERNAME,
            InitializationConfiguration.DEFAULT_USER_PASSWORD,
            List.of());
  }

  /**
   * Annotation to be passed along with {@link BrokerITInvocationProvider}'s {@link
   * org.junit.jupiter.api.TestTemplate}. When applied, this indicates that the {@link
   * CamundaClient} should be created with the provided user's credentials.
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  public @interface Authenticated {

    /** The username of the user to be used for authentication. */
    String value() default InitializationConfiguration.DEFAULT_USER_USERNAME;
  }
}
