/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

// TODO test with authorizations enabled
@MultiDbTest
public class BasicAuthMcpServerIT extends AuthenticatedMcpServerTest {

  @MultiDbTestApplication
  static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withAdditionalProfile("mcp");

  @UserDefinition
  private static final TestUser USER = new TestUser("mcp_username", "mcp_password", List.of());

  @Override
  protected TestCamundaApplication testInstance() {
    return TEST_INSTANCE;
  }

  @Override
  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    final var basicAuthHeader = createBasicAuthHeader();

    return (builder, method, endpoint, body, context) ->
        builder.header("Authorization", basicAuthHeader);
  }

  private String createBasicAuthHeader() {
    final var credentialsString = "%s:%s".formatted(USER.username(), USER.password());
    final var encodedCredentials =
        Base64.getEncoder().encodeToString(credentialsString.getBytes(StandardCharsets.UTF_8));

    return "Basic " + encodedCredentials;
  }
}
