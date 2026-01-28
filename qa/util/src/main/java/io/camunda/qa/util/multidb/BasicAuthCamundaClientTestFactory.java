/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.security.configuration.InitializationConfiguration;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BasicAuthCamundaClientTestFactory implements CamundaClientTestFactory {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BasicAuthCamundaClientTestFactory.class);

  private final Map<String, CamundaClient> cachedClients = new ConcurrentHashMap<>();

  public BasicAuthCamundaClientTestFactory(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress) {
    cachedClients.put(
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        createDefaultUserClient(camundaClientBuilder, restAddress, grpcAddress));
  }

  @Override
  public CamundaClient getAdminCamundaClient() {
    return getCamundaClient(InitializationConfiguration.DEFAULT_USER_USERNAME);
  }

  @Override
  public CamundaClient getCamundaClient(final String username) {
    return cachedClients.get(username);
  }

  @Override
  public CamundaClient getCamundaClient(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final Authenticated authenticated) {
    if (authenticated == null) {
      LOGGER.info("Creating unauthorized Camunda client for broker address '{}", restAddress);
      return camundaClientBuilder.restAddress(restAddress).preferRestOverGrpc(true).build();
    }

    LOGGER.info(
        "Retrieving Camunda client for user '{}' and broker address '{}",
        authenticated.value(),
        restAddress);
    final var username = authenticated.value();
    return cachedClients.get(username);
  }

  public void createClientForUser(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress,
      final TestUser user) {
    final var client =
        createAuthenticatedClient(
            camundaClientBuilder, restAddress, grpcAddress, user.username(), user.password());
    cachedClients.put(user.username(), client);
  }

  private CamundaClient createDefaultUserClient(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress) {
    return createAuthenticatedClient(
        camundaClientBuilder,
        restAddress,
        grpcAddress,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        InitializationConfiguration.DEFAULT_USER_PASSWORD);
  }

  private CamundaClient createAuthenticatedClient(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress,
      final String username,
      final String password) {
    return camundaClientBuilder
        .preferRestOverGrpc(true)
        .restAddress(restAddress)
        .grpcAddress(grpcAddress)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder().username(username).password(password).build())
        .build();
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(cachedClients.values());
  }
}
