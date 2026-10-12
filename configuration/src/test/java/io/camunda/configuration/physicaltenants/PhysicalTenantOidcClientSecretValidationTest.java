/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.configuration.UnifiedConfigurationException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantOidcClientSecretValidationTest {

  private static final String ROOT = "camunda.security.authentication.";
  private static final String TENANT = "camunda.physical-tenants.tenanta.security.authentication.";

  @Test
  void shouldRejectDefaultSlotWithOtherIssuerAndInheritedSecret() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "oidc.issuer-uri", "https://root-idp",
                ROOT + "oidc.client-secret", "root-secret",
                TENANT + "oidc.issuer-uri", "https://tenant-idp",
                TENANT + "providers.assigned[0]", "oidc"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .withMessageContaining("physical tenant 'tenanta', provider 'oidc'")
        .withMessageContaining(TENANT + "oidc.client-secret");
  }

  @Test
  void shouldRejectNamedProviderWithOtherIssuerAndInheritedSecret() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "providers.oidc.azure.issuer-uri", "https://root-idp",
                ROOT + "providers.oidc.azure.client-secret", "root-secret",
                TENANT + "providers.oidc.azure.issuer-uri", "https://tenant-idp",
                TENANT + "providers.assigned[0]", "azure"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .withMessageContaining("physical tenant 'tenanta', provider 'azure'")
        .withMessageContaining(TENANT + "providers.oidc.azure.client-secret");
  }

  @Test
  void shouldRejectOtherTokenUriWithInheritedSecret() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "oidc.token-uri", "https://root-idp/token",
                ROOT + "oidc.client-secret", "root-secret",
                TENANT + "oidc.token-uri", "https://tenant-idp/token",
                TENANT + "providers.assigned[0]", "oidc"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .withMessageContaining("provider 'oidc'");
  }

  @Test
  void shouldAcceptOtherIssuerWithOwnSecret() {
    // given — also a secret equal to the root secret, because the operator set it explicitly
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "oidc.issuer-uri", "https://root-idp",
                ROOT + "oidc.client-secret", "root-secret",
                TENANT + "oidc.issuer-uri", "https://tenant-idp",
                TENANT + "oidc.client-secret", "root-secret",
                TENANT + "providers.assigned[0]", "oidc"));

    // when / then
    assertThatCode(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptRestatedRootIssuer() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "oidc.issuer-uri", "https://root-idp",
                ROOT + "oidc.client-secret", "root-secret",
                TENANT + "oidc.issuer-uri", "https://root-idp",
                TENANT + "oidc.client-id", "tenant-client",
                TENANT + "providers.assigned[0]", "oidc"));

    // when / then
    assertThatCode(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptProviderNotAssignedToTenant() {
    // given — the tenant overrides the default slot but does not use it
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "oidc.issuer-uri", "https://root-idp",
                ROOT + "oidc.client-secret", "root-secret",
                ROOT + "providers.oidc.azure.issuer-uri", "https://azure-idp",
                TENANT + "oidc.issuer-uri", "https://tenant-idp",
                TENANT + "providers.assigned[0]", "azure"));

    // when / then
    assertThatCode(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptPrivateKeyJwtProvider() {
    // given — private_key_jwt sends no client secret
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "oidc.issuer-uri", "https://root-idp",
                ROOT + "oidc.client-secret", "root-secret",
                TENANT + "oidc.issuer-uri", "https://tenant-idp",
                TENANT + "oidc.client-authentication-method", "private_key_jwt",
                TENANT + "providers.assigned[0]", "oidc"));

    // when / then
    assertThatCode(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreNonOidcMethod() {
    // given
    final var environment =
        environmentWith(
            Map.of(
                ROOT + "method", "basic",
                ROOT + "oidc.issuer-uri", "https://root-idp",
                ROOT + "oidc.client-secret", "root-secret",
                TENANT + "oidc.issuer-uri", "https://tenant-idp"));

    // when / then
    assertThatCode(() -> PhysicalTenantOidcClientSecretValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  private static MockEnvironment environmentWith(final Map<String, Object> properties) {
    final Map<String, Object> withMethod = new HashMap<>(properties);
    withMethod.putIfAbsent(ROOT + "method", "oidc");
    final MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", withMethod));
    return environment;
  }
}
