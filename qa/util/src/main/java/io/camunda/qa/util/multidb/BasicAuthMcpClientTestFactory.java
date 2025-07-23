/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.ApplicationUnderTest;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BasicAuthMcpClientTestFactory implements McpClientTestFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicAuthMcpClientTestFactory.class);

  private final Map<String, McpSyncClient> cachedClients = new ConcurrentHashMap<>();

  public BasicAuthMcpClientTestFactory(final ApplicationUnderTest application) {
    cachedClients.put(
        InitializationConfiguration.DEFAULT_USER_USERNAME, createDefaultUserClient(application));
  }

  @Override
  public McpSyncClient getMcpClient(
      final TestGateway<?> gateway, final Authenticated authenticated) {
    if (authenticated == null) {
      LOGGER.info("Creating unauthorized Mcp client for broker address '{}", gateway.restAddress());
      final var transport = newTransport(gateway.restAddress(), null);
      return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    LOGGER.info(
        "Retrieving Mcp client for user '{}' and broker address '{}",
        authenticated.value(),
        gateway.restAddress());
    final var username = authenticated.value();
    return cachedClients.get(username);
  }

  private McpSyncClient createDefaultUserClient(final ApplicationUnderTest application) {
    return createAuthenticatedClient(application.application(), TestUser.DEFAULT);
  }

  private McpSyncClient createAuthenticatedClient(final TestGateway<?> gateway, TestUser testUser) {
    final var transport = newTransport(gateway.restAddress(), testUser);
    return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(cachedClients.values());
  }

  private static McpClientTransport newTransport(URI url, TestUser testUser) {
    final var builder = HttpClientSseClientTransport.builder(url.toString());
    if (testUser != null) {
      builder.customizeRequest(
          r ->
              r.header(
                  "Authorization",
                  "Basic "
                      + Base64.getEncoder()
                          .encodeToString(
                              (testUser.username() + ":" + testUser.password())
                                  .getBytes(StandardCharsets.UTF_8))));
    }
    return builder.build();
  }
}
