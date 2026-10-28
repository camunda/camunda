/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class OidcRevocationDefaultPostProcessorTest {

  private static final String ROOT_AUTHENTICATION = "camunda.security.authentication";

  private final OidcRevocationDefaultPostProcessor processor =
      new OidcRevocationDefaultPostProcessor();

  @Test
  void shouldDisableRevocationForPrimaryProviderByDefault() {
    // given
    final var environment =
        environmentWith(Map.of(ROOT_AUTHENTICATION + ".oidc.client-id", "orchestration"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(bindAuthentication(environment, ROOT_AUTHENTICATION).getOidc().isRevocationEnabled())
        .isFalse();
  }

  @Test
  void shouldKeepRevocationWhenExplicitlyEnabled() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT_AUTHENTICATION + ".oidc.client-id", "orchestration",
                ROOT_AUTHENTICATION + ".oidc.revocation-enabled", "true"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(bindAuthentication(environment, ROOT_AUTHENTICATION).getOidc().isRevocationEnabled())
        .isTrue();
  }

  @Test
  void shouldDisableRevocationForNamedProvidersByDefault() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT_AUTHENTICATION + ".providers.oidc.keycloak.client-id", "orchestration",
                ROOT_AUTHENTICATION + ".providers.oidc.entra.client-id", "orchestration",
                ROOT_AUTHENTICATION + ".providers.oidc.entra.revocation-enabled", "true"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    final var providers =
        bindAuthentication(environment, ROOT_AUTHENTICATION).getProviders().getOidc();
    assertThat(providers.get("keycloak").isRevocationEnabled()).isFalse();
    assertThat(providers.get("entra").isRevocationEnabled()).isTrue();
  }

  @Test
  void shouldDisableRevocationForPhysicalTenantProvidersByDefault() {
    // given
    final var tenantAuthentication = "camunda.physical-tenants.tenanta.security.authentication";
    final var environment =
        environmentWith(
            Map.of(
                tenantAuthentication + ".oidc.client-id", "orchestration",
                tenantAuthentication + ".providers.oidc.keycloak.client-id", "orchestration"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    final var authentication = bindAuthentication(environment, tenantAuthentication);
    assertThat(authentication.getOidc().isRevocationEnabled()).isFalse();
    assertThat(authentication.getProviders().getOidc().get("keycloak").isRevocationEnabled())
        .isFalse();
  }

  @Test
  void shouldRecognizeEnvironmentVariables() {
    // given
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of(
                    "CAMUNDA_SECURITY_AUTHENTICATION_PROVIDERS_OIDC_KEYCLOAK_CLIENTID",
                    "orchestration")));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(
            bindAuthentication(environment, ROOT_AUTHENTICATION)
                .getProviders()
                .getOidc()
                .get("keycloak")
                .isRevocationEnabled())
        .isFalse();
  }

  @Test
  void shouldKeepRootOptInForPhysicalTenantThatOverridesProvider() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT_AUTHENTICATION + ".oidc.client-id",
                "orchestration",
                ROOT_AUTHENTICATION + ".oidc.revocation-enabled",
                "true",
                "camunda.physical-tenants.tenanta.security.authentication.oidc.client-id",
                "tenant-a"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(bindTenantAuthentication(environment, "tenanta").getOidc().isRevocationEnabled())
        .isTrue();
  }

  @Test
  void shouldRecognizePhysicalTenantEnvironmentVariables() {
    // given
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of(
                    "CAMUNDA_PHYSICALTENANTS_TENANTA_SECURITY_AUTHENTICATION_OIDC_CLIENTID",
                    "tenant-a")));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(bindTenantAuthentication(environment, "tenanta").getOidc().isRevocationEnabled())
        .isFalse();
  }

  @Test
  void shouldNotAddOidcPropertiesWhenOidcIsNotConfigured() {
    // given
    final var environment = environmentWith(Map.of(ROOT_AUTHENTICATION + ".method", "basic"));

    // when
    processor.postProcessEnvironment(environment, null);

    // then
    assertThat(environment.getPropertySources().contains(OidcRevocationDefaultPostProcessor.NAME))
        .isFalse();
  }

  private static StandardEnvironment environmentWith(final Map<String, Object> properties) {
    final var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  private static AuthenticationConfiguration bindAuthentication(
      final StandardEnvironment environment, final String prefix) {
    return Binder.get(environment).bindOrCreate(prefix, AuthenticationConfiguration.class);
  }

  /**
   * Binds a physical tenant's configuration the way PhysicalTenantResolver does: root, then tenant.
   */
  private static AuthenticationConfiguration bindTenantAuthentication(
      final StandardEnvironment environment, final String tenantId) {
    final var authentication = new AuthenticationConfiguration();
    final var binder = Binder.get(environment);
    binder.bind(ROOT_AUTHENTICATION, Bindable.ofInstance(authentication));
    binder.bind(
        "camunda.physical-tenants." + tenantId + ".security.authentication",
        Bindable.ofInstance(authentication));
    return authentication;
  }
}
