/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static io.camunda.zeebe.engine.processing.user.DefaultUserCreator.DEFAULT_USER_PASSWORD;
import static io.camunda.zeebe.engine.processing.user.DefaultUserCreator.DEFAULT_USER_USERNAME;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.awaitility.Awaitility;

public final class ZeebeClientTestFactory implements AutoCloseable {

  private final Map<String, User> usersRegistry = new HashMap<>();
  private final Map<String, ZeebeClient> cachedClients = new ConcurrentHashMap<>();

  public ZeebeClientTestFactory() {
    usersRegistry.put(DEFAULT_USER_USERNAME, User.DEFAULT);
  }

  public void registerUsers(final User... users) {
    Stream.of(users).forEach(user -> usersRegistry.put(user.username(), user));
  }

  public ZeebeClient createZeebeClient(
      final TestGateway<?> gateway, final Authenticated authenticated) {
    if (authenticated == null) {
      return gateway.newClientBuilder().build();
    }
    final ZeebeClient defaultClient =
        cachedClients.computeIfAbsent(
            DEFAULT_USER_USERNAME, __ -> createDefaultUserClient(gateway));
    final String username = authenticated.value();
    if (DEFAULT_USER_USERNAME.equals(username)) {
      return defaultClient;
    } else {
      return cachedClients.computeIfAbsent(
          username, k -> createClientForUser(gateway, defaultClient, k));
    }
  }

  private ZeebeClient createClientForUser(
      final TestGateway<?> gateway, final ZeebeClient defaultClient, final String username) {
    final User user = usersRegistry.get(username);
    createUserWithPermissions(defaultClient, user.username(), user.password(), user.permissions());
    return createAuthenticatedClient(gateway, user.username(), user.password());
  }

  private ZeebeClient createDefaultUserClient(final TestGateway<?> gateway) {
    final ZeebeClient defaultClient =
        createAuthenticatedClient(gateway, DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD);
    // check in case the default user is not created yet
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .until(() -> defaultClient.newTopologyRequest().send().join().getBrokers().size() > 0);
    return defaultClient;
  }

  private void createUserWithPermissions(
      final ZeebeClient defaultClient,
      final String username,
      final String password,
      final List<Permissions> permissions) {
    final var userCreateResponse =
        defaultClient
            .newUserCreateCommand()
            .username(username)
            .password(password)
            .name(username)
            .email("%s@foo.com".formatted(username))
            .send()
            .join();

    for (final Permissions permission : permissions) {
      defaultClient
          .newAddPermissionsCommand(userCreateResponse.getUserKey())
          .resourceType(permission.resourceType())
          .permission(permission.permissionType())
          .resourceIds(permission.resourceIds())
          .send()
          .join();
    }
    // TODO replace with proper user get by key once it is implemented
    try {
      Thread.sleep(2000);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private ZeebeClient createAuthenticatedClient(
      final TestGateway<?> gateway, final String username, final String password) {
    return gateway
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new CredentialsProvider() {
              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put(
                    "Authorization",
                    "Basic %s"
                        .formatted(
                            Base64.getEncoder()
                                .encodeToString("%s:%s".formatted(username, password).getBytes())));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode statusCode) {
                return false;
              }
            })
        .build();
  }

  @Override
  public void close() {
    cachedClients.values().forEach(ZeebeClient::close);
    cachedClients.clear();
  }

  public record Permissions(
      ResourceTypeEnum resourceType, PermissionTypeEnum permissionType, List<String> resourceIds) {}

  public record User(String username, String password, List<Permissions> permissions) {
    public static final User DEFAULT =
        new User(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD, List.of());
  }

  /**
   * Annotation to be passed along with {@link BrokerWithCamundaExporterITInvocationProvider}'s
   * {@link org.junit.jupiter.api.TestTemplate}. When applied, this indicates that the ZeebeClient
   * should be created with the provided user's credentials.
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  public @interface Authenticated {

    /** The username of the user to be used for authentication. */
    String value() default DEFAULT_USER_USERNAME;
  }
}
