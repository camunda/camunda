/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

final class PhysicalTenantCredentialsTest {

  private static final String TENANT = "camunda.physical-tenants.tenanta.security.authentication.";

  @Test
  void shouldReadTheCredentialsOfTheDefaultSlotAndEveryNamedProvider() {
    // given
    final var authentication =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", environment());

    // when
    final var credentials = PhysicalTenantCredentials.of(authentication);

    // then
    assertThat(credentials)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "oidc.client-secret", "camunda.secrets.client",
                "oidc.assertion.keystore.path", "/keys/tenanta.p12",
                "oidc.assertion.keystore.password", "camunda.secrets.store",
                "oidc.assertion.keystore.key-password", "camunda.secrets.key",
                "providers.oidc.idp.client-secret", "camunda.secrets.idp"));
    assertThat(PhysicalTenantCredentials.keystorePaths(credentials))
        .containsExactly("/keys/tenanta.p12");
  }

  @Test
  void shouldSetResolvedValuesOnAConfigurationBuiltForTheChange() {
    // given
    final var authentication =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", environment());

    // when
    PhysicalTenantCredentials.withValues(
        authentication,
        Map.of(
            "oidc.client-secret", "resolved-client",
            "oidc.assertion.keystore.password", "resolved-store",
            "providers.oidc.idp.client-secret", "resolved-idp"));

    // then
    assertThat(authentication.getOidc().getClientSecret()).isEqualTo("resolved-client");
    assertThat(authentication.getOidc().getAssertion().getKeystore().getPassword())
        .isEqualTo("resolved-store");
    assertThat(authentication.getOidc().getAssertion().getKeystore().getKeyPassword())
        .isEqualTo("camunda.secrets.key");
    assertThat(authentication.getProviders().getOidc().get("idp").getClientSecret())
        .isEqualTo("resolved-idp");
  }

  private static MockEnvironment environment() {
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");
    environment.setProperty(TENANT + "oidc.issuer-uri", "https://idp/a");
    environment.setProperty(TENANT + "oidc.client-id", "client-a");
    environment.setProperty(TENANT + "oidc.client-secret", "camunda.secrets.client");
    environment.setProperty(TENANT + "oidc.assertion.keystore.path", "/keys/tenanta.p12");
    environment.setProperty(TENANT + "oidc.assertion.keystore.password", "camunda.secrets.store");
    environment.setProperty(TENANT + "oidc.assertion.keystore.key-password", "camunda.secrets.key");
    environment.setProperty(TENANT + "providers.oidc.idp.issuer-uri", "https://idp/b");
    environment.setProperty(TENANT + "providers.oidc.idp.client-id", "client-b");
    environment.setProperty(TENANT + "providers.oidc.idp.client-secret", "camunda.secrets.idp");
    environment.setProperty(TENANT + "providers.assigned[0]", "oidc");
    environment.setProperty(TENANT + "providers.assigned[1]", "idp");
    return environment;
  }
}
