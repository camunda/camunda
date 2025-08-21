/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class OidcAuthOverRestInitializerIT {

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final String KEYCLOAK_REALM = "camunda";
  private static final String DEFAULT_CLIENT_ID = "zeebe";
  private static final String DEFAULT_CLIENT_SECRET = "secret";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

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
                oidcConfig.setIssuerUri(KEYCLOAK.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM);

                // The following two properties are only needed for the webapp login flow which we
                // don't test here.
                oidcConfig.setClientId("example");
                oidcConfig.setRedirectUri("example.com");
                // add a preconfigured user. This should not be allowed with OIDC
                c.getInitialization()
                    .getUsers()
                    .add(
                        new ConfiguredUser(
                            "someUser", "somepassword", "Some User", "some_user@email.com"));
              });

  @BeforeAll
  static void setupKeycloak() {
    final var defaultClient = new ClientRepresentation();
    defaultClient.setClientId(DEFAULT_CLIENT_ID);
    defaultClient.setEnabled(true);
    defaultClient.setClientAuthenticatorType("client-secret");
    defaultClient.setSecret(DEFAULT_CLIENT_SECRET);
    defaultClient.setServiceAccountsEnabled(true);

    final var defaultUser = new UserRepresentation();
    defaultUser.setId(DEFAULT_USER_ID);
    defaultUser.setUsername("zeebe-service-account");
    defaultUser.setServiceAccountClientId(DEFAULT_CLIENT_ID);
    defaultUser.setEnabled(true);

    final var realm = new RealmRepresentation();
    realm.setRealm("camunda");
    realm.setEnabled(true);
    realm.setClients(List.of(defaultClient));
    realm.setUsers(List.of(defaultUser));

    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }
  }

  @Test
  public void shouldFailToStartWithPreConfiguredUsersInOidc() {
    // given
    final String expectedMessage =
        "Creation of initial users is not supported with `OIDC` authentication method";
    // when
    final Throwable throwable = Assertions.assertThatThrownBy(broker::start).actual();
    // then
    assertHasNestedException(throwable, IllegalStateException.class, expectedMessage);
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
