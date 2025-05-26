/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.User;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamundaClientTestFactory implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaClientTestFactory.class);

  private final Map<String, CamundaClient> cachedClients = new ConcurrentHashMap<>();

  public CamundaClientTestFactory(final TestStandaloneApplication<?> application) {
    cachedClients.put(
        InitializationConfiguration.DEFAULT_USER_USERNAME, createDefaultUserClient(application));
  }

  public CamundaClient getDefaultUserCamundaClient() {
    return getCamundaClient(InitializationConfiguration.DEFAULT_USER_USERNAME);
  }

  public CamundaClient getCamundaClient(final String username) {
    return cachedClients.get(username);
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

  public void createClientForUser(final TestGateway<?> gateway, final User user) {
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
