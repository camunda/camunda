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
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OidcCamundaClientTestFactory implements CamundaClientTestFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(OidcCamundaClientTestFactory.class);

  private final Map<String, CamundaClient> cachedClients = new ConcurrentHashMap<>();
  private final Path tempDirectory;
  private final String authorizationServerUrl;
  private final CamundaClient adminCamundaClient;

  public OidcCamundaClientTestFactory(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress,
      final String testPrefix,
      final String authorizationServerUrl)
      throws IOException {
    tempDirectory = Files.createTempDirectory(testPrefix);
    this.authorizationServerUrl = authorizationServerUrl;
    adminCamundaClient =
        createAuthenticatedClient(
            camundaClientBuilder,
            restAddress,
            grpcAddress,
            TestStandaloneBroker.DEFAULT_MAPPING_RULE_ID,
            TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_VALUE);
  }

  @Override
  public CamundaClient getAdminCamundaClient() {
    if (adminCamundaClient == null) {
      throw new IllegalStateException(
          "No admin Camunda client has been created. Ensure that the OIDC mappings are configured correctly.");
    }
    return adminCamundaClient;
  }

  @Override
  public CamundaClient getCamundaClient(final String mappingRuleId) {
    return cachedClients.get(mappingRuleId);
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

    final var mappingRuleId = authenticated.value();
    LOGGER.info(
        "Retrieving Camunda client for mapping rule id '{}' and broker address '{}",
        mappingRuleId,
        restAddress);
    return cachedClients.get(mappingRuleId);
  }

  public void createClientForMappingRule(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress,
      final TestMappingRule mappingRule) {
    final var client =
        createAuthenticatedClient(
            camundaClientBuilder,
            restAddress,
            grpcAddress,
            mappingRule.id(),
            mappingRule.claimValue());
    cachedClients.put(mappingRule.id(), client);
  }

  public void createClientForClient(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress,
      final TestClient client) {
    final var camundaClient =
        createAuthenticatedClient(
            camundaClientBuilder, restAddress, grpcAddress, client.clientId(), client.clientId());
    cachedClients.put(client.clientId(), camundaClient);
  }

  private CamundaClient createAuthenticatedClient(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final URI grpcAddress,
      final String mappingRuleId,
      final String claimValue) {
    final var client =
        camundaClientBuilder
            .preferRestOverGrpc(true)
            .restAddress(restAddress)
            .grpcAddress(grpcAddress)
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(claimValue)
                    .clientSecret(claimValue)
                    .audience("zeebe")
                    .authorizationServerUrl(authorizationServerUrl)
                    .credentialsCachePath(tempDirectory.resolve("default").toString())
                    .build())
            .build();
    cachedClients.put(mappingRuleId, client);
    return client;
  }

  @Override
  public void close() {
    deleteTempDirectory();
    CloseHelper.quietCloseAll(cachedClients.values());
  }

  private void deleteTempDirectory() {
    try (final var paths = Files.walk(tempDirectory)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (final IOException e) {
                  LOGGER.error("Failed to delete temporary file: {}", p, e);
                }
              });
    } catch (final IOException e) {
      LOGGER.error("Failed to delete temporary directory: {}", tempDirectory, e);
    }
  }
}
