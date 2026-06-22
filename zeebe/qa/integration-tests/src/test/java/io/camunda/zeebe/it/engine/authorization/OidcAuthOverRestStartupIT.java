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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
                c.getAuthentication().getOidc().setRedirectUri("example.com");
                c.getAuthorizations().setEnabled(true);
                final var defaultRoles = new HashMap<>(c.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER_ID)));
                c.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Test
  public void shouldFailToStartWhenNoIdpAvailable() {
    // The startup must fail because the configured IdP is unreachable. The exact exception type
    // and message come from Spring Security's ClientRegistrations.fromIssuerLocation(...); we
    // assert only that the failure mentions the unreachable issuer URI, not the specific message
    // text — OC's previous ClientRegistrationFactory wrapped the cause in a friendly diagnostic
    // but CSL surfaces Spring's raw error, so a verbatim string match is no longer stable.
    Assertions.assertThatThrownBy(broker::start)
        .satisfiesAnyOf(
            ex -> Assertions.assertThat(ex).hasStackTraceContaining(UNREACHABLE_ISSUER_URI),
            ex -> Assertions.assertThat(ex).hasStackTraceContaining("localhost:1000"));
  }
}
