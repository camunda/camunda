/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.authorization;

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * An unreachable identity provider must not keep the broker from starting. Issuer discovery used to
 * run while the Spring context came up, so a provider that was down at boot failed a bean and left
 * the deployment restart-looping until the provider returned. Discovery now happens on first use,
 * so the broker starts and only the requests that need the provider fail.
 */
@Testcontainers
@ZeebeIntegration
public class OidcAuthOverRestStartupIT {
  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final String KEYCLOAK_REALM = "camunda";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static final String UNREACHABLE_ISSUER_URI =
      "http://localhost:1000/realms/" + KEYCLOAK_REALM;

  @TestZeebe(autoStart = false, awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
          .withSecurityConfig(
              c -> {
                c.getAuthentication().getOidc().setIssuerUri(UNREACHABLE_ISSUER_URI);
                c.getAuthentication().getOidc().setClientId("example");
                c.getAuthentication().getOidc().setRedirectUri("https://example.com");
                c.getAuthorizations().setEnabled(true);
                final var defaultRoles = new HashMap<>(c.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER_ID)));
                c.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Test
  public void shouldStartWhenNoIdpAvailable() throws IOException, InterruptedException {
    // when
    broker.start();

    // then the broker is up and serving: a request carrying a token it cannot validate against the
    // unreachable provider fails on its own, as a server error, rather than taking the broker down
    Assertions.assertThat(statusOfTopologyRequestWithBearerToken()).isEqualTo(500);
  }

  private int statusOfTopologyRequestWithBearerToken() throws IOException, InterruptedException {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(broker.restAddress().resolve("v2/topology"))
              .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.not-a-sig")
              .timeout(Duration.ofSeconds(30))
              .GET()
              .build();
      return httpClient.send(request, BodyHandlers.discarding()).statusCode();
    }
  }
}
