/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

// TODO test with authorizations enabled
@MultiDbTest(setupKeycloak = true)
public class OidcMcpServerIT extends AuthenticatedMcpServerTest {

  @MultiDbTestApplication
  static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthenticatedAccess()
          .withSecurityConfig(c -> c.getInitialization().getUsers().clear())
          .withAdditionalProfile("mcp");

  // Injected by the MultiDbTest extension
  private static KeycloakContainer keycloak;

  @ClientDefinition
  private static final TestClient MCP_CLIENT = new TestClient("mcpClient", List.of());

  @Override
  protected TestCamundaApplication testInstance() {
    return TEST_INSTANCE;
  }

  @Override
  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    return (builder, method, endpoint, body, context) ->
        builder.header("Authorization", "Bearer " + getAccessToken(MCP_CLIENT));
  }

  private static String getAccessToken(final TestClient testClient) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.put("grant_type", singletonList("client_credentials"));
    map.put("client_id", singletonList(testClient.clientId()));
    map.put("client_secret", singletonList(testClient.clientId()));

    final RestTemplate restTemplate = new RestTemplate();
    final KeycloakToken token =
        restTemplate.postForObject(
            keycloak.getAuthServerUrl() + "/realms/camunda/protocol/openid-connect/token",
            new HttpEntity<>(map, httpHeaders),
            KeycloakToken.class);

    assertThat(token).isNotNull();
    assertThat(token.accessToken()).isNotNull();

    return token.accessToken();
  }

  private record KeycloakToken(@JsonProperty("access_token") String accessToken) {}
}
