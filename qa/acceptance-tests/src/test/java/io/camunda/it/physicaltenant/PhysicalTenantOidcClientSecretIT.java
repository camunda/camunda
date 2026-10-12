/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A physical tenant inherits every OIDC field it does not set from the cluster provider with the
 * same id. When the tenant points that provider at a different identity provider, an inherited
 * client secret would be sent to that other identity provider. The broker must refuse to start in
 * that case, and must start when the tenant sets its own secret.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantOidcClientSecretIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_ID = "tenanta";
  private static final String ROOT_REALM = "realm-root";
  private static final String TENANT_REALM = "realm-tenant";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("default"))
          .withTenant(TENANT_ID, Storage.rdbmsH2(TENANT_ID))
          .build();

  @BeforeAll
  static void createRealms() {
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      List.of(ROOT_REALM, TENANT_REALM)
          .forEach(
              name -> {
                final var realm = new RealmRepresentation();
                realm.setRealm(name);
                realm.setEnabled(true);
                admin.realms().create(realm);
              });
    }
  }

  @Test
  void shouldFailToStartWhenTenantIssuerInheritsTheClusterClientSecret() {
    // given — the tenant points the default slot at another IdP but sets no client secret
    final var broker = brokerWithTenantOverride(null);

    // when/then
    try {
      assertThatThrownBy(broker::start)
          .rootCause()
          .isInstanceOf(UnifiedConfigurationException.class)
          .hasMessageContaining("physical tenant '" + TENANT_ID + "'")
          .hasMessageContaining("'oidc'")
          .hasMessageContaining("client-secret");
    } finally {
      broker.close();
    }
  }

  @Test
  void shouldStartWhenTenantIssuerHasItsOwnClientSecret() {
    // given — the tenant sets its own secret together with the other IdP
    final var broker = brokerWithTenantOverride("tenant-secret");

    // when/then
    try {
      assertThatCode(broker::start).doesNotThrowAnyException();
    } finally {
      broker.close();
    }
  }

  private static TestStandaloneBroker brokerWithTenantOverride(final String tenantClientSecret) {
    final var broker =
        TENANTS.configure(
            new TestStandaloneBroker()
                .withAuthenticatedAccess()
                .withAuthenticationMethod(AuthenticationMethod.OIDC));
    broker.withSecurityConfig(
        c -> {
          final var oidc = c.getAuthentication().getOidc();
          oidc.setIssuerUri(issuer(ROOT_REALM));
          oidc.setClientId("root-client");
          oidc.setClientSecret("root-secret");
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    broker.withPtConfig(
        TENANT_ID,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuer(TENANT_REALM));
          oidc.setClientId("tenant-client");
          if (tenantClientSecret != null) {
            oidc.setClientSecret(tenantClientSecret);
          }
        });
    broker.withProperty(
        "camunda.physical-tenants." + TENANT_ID + ".security.authentication.providers.assigned[0]",
        "oidc");
    return broker;
  }

  private static String issuer(final String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }
}
