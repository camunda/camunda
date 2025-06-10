/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.migration.identity.config.ConsoleConnectorConfig.TokenInterceptor;
import io.camunda.migration.identity.config.ConsoleProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.console.ConsoleClient.Permission;
import io.camunda.migration.identity.console.ConsoleClient.Role;
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
              "originalUserId": "user123",
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
    consoleProps.setBaseUrl("http://localhost:" + wmRuntimeInfo.getHttpPort());
    consoleProps.setIssuerBackendUrl("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/oauth/token");
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
    assertNotNull(members);
    assertEquals(1, members.members().size());
    assertEquals("John Doe", members.members().getFirst().name());
    assertEquals("user123", members.members().getFirst().originalUserId());
    assertEquals("user@example.com", members.members().getFirst().email());
    assertEquals(2, members.members().getFirst().roles().size());
    assertEquals(Role.ADMIN, members.members().getFirst().roles().get(0));
    assertEquals(Role.DEVELOPER, members.members().getFirst().roles().get(1));
    assertEquals(1, members.clients().size());
    assertEquals("console-client", members.clients().getFirst().name());
    assertEquals("client123", members.clients().getFirst().clientId());
    assertEquals(2, members.clients().getFirst().permissions().size());
    assertEquals(Permission.OPERATE, members.clients().getFirst().permissions().get(0));
    assertEquals(Permission.ZEEBE, members.clients().getFirst().permissions().get(1));
  }
}
