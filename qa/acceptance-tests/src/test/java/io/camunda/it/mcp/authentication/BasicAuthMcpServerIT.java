/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp.authentication;

import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@MultiDbTest
public class BasicAuthMcpServerIT extends AuthenticatedMcpServerTest {

  @MultiDbTestApplication
  static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withProperty("camunda.mcp.enabled", true);

  @UserDefinition
  private static final TestUser UNRESTRICTED_USER =
      new TestUser(
          UNRESTRICTED_PRINCIPAL_NAME, "test_unrestricted_password", UNRESTRICTED_PERMISSIONS);

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED_PRINCIPAL_NAME, "test_restricted_password", RESTRICTED_PERMISSIONS);

  @Override
  protected TestCamundaApplication testInstance() {
    return TEST_INSTANCE;
  }

  @Override
  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    return createBasicAuthAuthenticator(UNRESTRICTED_USER);
  }

  @Override
  protected McpSyncHttpClientRequestCustomizer createRestrictedMcpClientRequestCustomizer() {
    return createBasicAuthAuthenticator(RESTRICTED_USER);
  }

  private McpSyncHttpClientRequestCustomizer createBasicAuthAuthenticator(final TestUser user) {
    final var credentialsString = "%s:%s".formatted(user.username(), user.password());
    final var encodedCredentials =
        Base64.getEncoder().encodeToString(credentialsString.getBytes(StandardCharsets.UTF_8));

    return (builder, method, endpoint, body, context) ->
        builder.header("Authorization", "Basic " + encodedCredentials);
  }
}
