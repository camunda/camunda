/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static io.camunda.client.api.search.enums.ResourceType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.StatusResponse;
import io.camunda.client.api.response.StatusResponse.Status;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class GlobalErrorControllerWithAuthIT {

  private static final String ADMIN = "admin";
  private static final String PASSWORD = "password";
  private static final String UNAUTHORIZED = "unauthorizedUser";

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          PASSWORD,
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(SYSTEM, READ_USAGE_METRIC, List.of("*")),
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*")),
              new Permissions(TENANT, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER = new TestUser(UNAUTHORIZED, PASSWORD, List.of());

  @Test
  void shouldPass() throws URISyntaxException {
    try (final CamundaClient client = createClient(ADMIN_USER, "")) {

      final UsageMetricsStatistics usageMetrics =
          client
              .newUsageMetricsRequest(OffsetDateTime.now().minusDays(1), OffsetDateTime.now())
              .send()
              .join();

      assertThat(usageMetrics).isNotNull();
    }
  }

  @Test
  void shouldPassForUnauthenticatedEndpoint() throws URISyntaxException {
    try (final CamundaClient client = createClient(null, "")) {

      final StatusResponse statusResponse = client.newStatusRequest().send().join();
      assertThat(statusResponse).isNotNull();
      assertThat(statusResponse.getStatus()).isEqualTo(Status.UP);
    }
  }

  @Test
  void shouldThrowClientExceptionUnauthorized() throws URISyntaxException {
    try (final CamundaClient client = createClient(UNAUTHORIZED_USER, "/wrong")) {

      assertThatThrownBy(
              () ->
                  client
                      .newUsageMetricsRequest(
                          OffsetDateTime.now().minusDays(1), OffsetDateTime.now())
                      .send()
                      .join())
          .isInstanceOf(ClientException.class)
          .hasMessage(
              "Failed with code 404: 'Not Found'. Details: 'class ProblemDetail {\n"
                  + "    type: about:blank\n"
                  + "    title: Not Found\n"
                  + "    status: 404\n"
                  + "    detail: No message available\n"
                  + "    instance: /wrong/v2/system/usage-metrics\n"
                  + "}'");
    }
  }

  @Test
  void shouldThrowClientExceptionAuthorized() throws URISyntaxException {
    try (final CamundaClient client = createClient(ADMIN_USER, "/wrong")) {

      assertThatThrownBy(
              () ->
                  client
                      .newUsageMetricsRequest(
                          OffsetDateTime.now().minusDays(1), OffsetDateTime.now())
                      .send()
                      .join())
          .isInstanceOf(ClientException.class)
          .hasMessage(
              "Failed with code 404: 'Not Found'. Details: 'class ProblemDetail {\n"
                  + "    type: about:blank\n"
                  + "    title: Not Found\n"
                  + "    status: 404\n"
                  + "    detail: No message available\n"
                  + "    instance: /wrong/v2/system/usage-metrics\n"
                  + "}'");
    }
  }

  private static CamundaClient createClient(final TestUser user, final String path)
      throws URISyntaxException {
    final CamundaClientBuilder camundaClientBuilder =
        BROKER
            .newClientBuilder()
            .restAddress(
                new URI("http://localhost:" + BROKER.mappedPort(TestZeebePort.REST) + path))
            .defaultRequestTimeout(Duration.ofMinutes(10))
            .preferRestOverGrpc(true);
    if (user != null) {
      camundaClientBuilder.credentialsProvider(
          new BasicAuthCredentialsProviderBuilder()
              .username(user.username())
              .password(user.password())
              .build());
    } else {
      camundaClientBuilder.credentialsProvider(null);
    }
    return camundaClientBuilder.build();
  }
}
