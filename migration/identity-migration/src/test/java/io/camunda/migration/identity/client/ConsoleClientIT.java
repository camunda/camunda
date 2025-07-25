/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.migration.identity.client.ConsoleClient.Permission;
import io.camunda.migration.identity.client.ConsoleClient.Role;
import io.camunda.migration.identity.config.ConsoleProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.saas.ConsoleConnectorConfig.TokenInterceptor;
import java.text.MessageFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

@WireMockTest
public class ConsoleClientIT {

  private ConsoleClient consoleClient;

  @BeforeEach
  void setup(final WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    final String token = "mocked-access-token";

    stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"access_token\": \"" + token + "\"}")));

    final String endpoint =
        MessageFormat.format(
            "/external/organizations/{0}/clusters/{1}/migrationData/{2}",
            "org123", "cluster123", "client123");

    final String responseJson =
        """
        {
          "members": [
            {
              "userId": "user123",
              "roles": ["admin", "developer"],
              "email": "user@example.com",
              "name": "John Doe"
            }
          ],
          "clients": [
            {
              "name": "console-client",
              "clientId": "client123",
              "permissions": ["Operate", "Zeebe"]
            }
          ]
        }
        """;

    stubFor(
        get(urlEqualTo(endpoint))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseJson)));

    final ConsoleProperties consoleProps = new ConsoleProperties();
    consoleProps.setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
    consoleProps.setIssuerBackendUrl(wmRuntimeInfo.getHttpBaseUrl() + "/oauth/token");
    consoleProps.setClientId("client-id");
    consoleProps.setClientSecret("client-secret");
    consoleProps.setAudience("test-audience");
    consoleProps.setClusterId("cluster123");
    consoleProps.setInternalClientId("client123");

    final IdentityMigrationProperties props = new IdentityMigrationProperties();
    props.setOrganizationId("org123");
    props.setConsole(consoleProps);

    final RestTemplate restTemplate =
        new RestTemplateBuilder()
            .rootUri(consoleProps.getBaseUrl())
            .interceptors(new TokenInterceptor(consoleProps))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();

    consoleClient = new ConsoleClient(props, restTemplate);
  }

  @Test
  void testFetchMembers() {
    final ConsoleClient.Members members = consoleClient.fetchMembers();
    assertThat(members).isNotNull();
    assertThat(members.members().size()).isEqualTo(1);
    assertThat(members.members().getFirst().name()).isEqualTo("John Doe");
    assertThat(members.members().getFirst().userId()).isEqualTo("user123");
    assertThat(members.members().getFirst().email()).isEqualTo("user@example.com");
    assertThat(members.members().getFirst().roles().size()).isEqualTo(2);
    assertThat(members.members().getFirst().roles().get(0)).isEqualTo(Role.ADMIN);
    assertThat(members.members().getFirst().roles().get(1)).isEqualTo(Role.DEVELOPER);
    assertThat(members.clients().size()).isEqualTo(1);
    assertThat(members.clients().getFirst().name()).isEqualTo("console-client");
    assertThat(members.clients().getFirst().clientId()).isEqualTo("client123");
    assertThat(members.clients().getFirst().permissions().size()).isEqualTo(2);
    assertThat(members.clients().getFirst().permissions().get(0)).isEqualTo(Permission.OPERATE);
    assertThat(members.clients().getFirst().permissions().get(1)).isEqualTo(Permission.ZEEBE);
  }
}
