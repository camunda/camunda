/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Fail;
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
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @TestZeebe(autoStart = false, awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
          .withSecurityConfig(
              c -> {
                c.getAuthorizations().setEnabled(true);
                final var oidcConfig = c.getAuthentication().getOidc();
                final String issuerUri = "http://localhost:1000" + "/realms/" + KEYCLOAK_REALM;
                oidcConfig.setIssuerUri(issuerUri);
                // The following two properties are only needed for the webapp login flow which we
                // don't test here.
                oidcConfig.setClientId("example");
                oidcConfig.setRedirectUri("example.com");
                c.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER_ID)));
              });

  @Test
  public void shouldFailToStartWhenNoIdpAvailable() {
    // given
    final String expectedMessage =
        "Unable to connect to the Identity Provider endpoint `http://localhost:1000/realms/camunda'. "
            + "Double check that it is configured correctly, and if the problem persists, "
            + "contact your external Identity provider.";

    // when
    final Throwable exception = Assertions.assertThatThrownBy(broker::start).actual();

    // then
    assertHasNestedException(exception, IllegalStateException.class, expectedMessage);
  }

  private void assertHasNestedException(
      Throwable exception, final Class<?> expectedClass, final String expectedMessage) {
    while (exception != null) {
      if (expectedClass.equals(exception.getClass())
          && expectedMessage.equals(exception.getMessage())) {
        return;
      }
      exception = exception.getCause();
    }
    Fail.fail(
        "Exception has no cause with expectedClass: "
            + expectedClass.getSimpleName()
            + " and message: "
            + expectedMessage);
  }
}
