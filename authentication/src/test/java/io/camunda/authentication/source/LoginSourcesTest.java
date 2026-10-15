/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LoginSourcesTest {

  private static MockEnvironment scim() {
    return new MockEnvironment().withProperty("camunda.scim.enabled", "true");
  }

  private static MockEnvironment twoSources() {
    return scim()
        .withProperty("camunda.scim.sources[0].id", "entra")
        .withProperty("camunda.scim.sources[0].oidc-registration", "entra-reg")
        .withProperty("camunda.scim.sources[0].login-binding-claim", "oid")
        .withProperty("camunda.scim.sources[1].id", "keycloak")
        .withProperty("camunda.scim.sources[1].oidc-registration", "kc-reg")
        .withProperty(
            "camunda.security.authentication.providers.oidc.entra-reg.issuer-uri",
            "https://entra.example.com")
        .withProperty(
            "camunda.security.authentication.providers.oidc.kc-reg.issuer-uri",
            "https://kc.example.com");
  }

  @Test
  void shouldBeInactiveUnlessScimIsEnabled() {
    // given
    final var environment = new MockEnvironment();

    // when
    final var sources = LoginSources.from(environment);

    // then
    assertThat(sources.enabled()).isFalse();
    assertThat(sources.sourceOfRegistration("oidc")).isNull();
  }

  @Test
  void shouldResolveTheSourceOfASessionLoginByRegistration() {
    // given
    final var sources = LoginSources.from(twoSources());

    // when / then
    assertThat(sources.sourceOfRegistration("entra-reg")).isEqualTo("entra");
    assertThat(sources.sourceOfRegistration("kc-reg")).isEqualTo("keycloak");
    assertThat(sources.sourceOfRegistration("unknown")).isNull();
    assertThat(sources.source("entra").orElseThrow().loginBindingClaim()).isEqualTo("oid");
  }

  @Test
  void shouldResolveTheSourceOfABearerTokenByIssuerThroughItsRegistration() {
    // given
    final var sources = LoginSources.from(twoSources());

    // when / then
    assertThat(sources.sourceOfIssuer("https://entra.example.com")).isEqualTo("entra");
    assertThat(sources.sourceOfIssuer("https://kc.example.com")).isEqualTo("keycloak");
    assertThat(sources.sourceOfIssuer("https://other.example.com")).isNull();
    assertThat(sources.sourceOfIssuer(null)).isNull();
  }

  @Test
  void shouldFallBackToTheImplicitDefaultSourceWithoutConfiguredSources() {
    // given
    final var sources =
        LoginSources.from(
            scim()
                .withProperty(
                    "camunda.security.authentication.oidc.issuer-uri", "https://root.example.com"));

    // when / then
    assertThat(sources.ids()).containsExactly("default");
    assertThat(sources.sourceOfRegistration("oidc")).isEqualTo("default");
    assertThat(sources.sourceOfRegistration("anything")).isEqualTo("default");
    assertThat(sources.sourceOfIssuer("https://root.example.com")).isEqualTo("default");
  }

  @Test
  void shouldFallBackToTheOnlySourceWithoutRegistration() {
    // given
    final var sources =
        LoginSources.from(
            scim()
                .withProperty("camunda.scim.sources[0].id", "bound")
                .withProperty("camunda.scim.sources[0].oidc-registration", "a")
                .withProperty("camunda.scim.sources[1].id", "open"));

    // when / then
    assertThat(sources.sourceOfRegistration("a")).isEqualTo("bound");
    assertThat(sources.sourceOfRegistration("b")).isEqualTo("open");
  }

  @Test
  void shouldResolveNoSourceWhenSeveralSourcesHaveNoRegistration() {
    // given
    final var sources =
        LoginSources.from(
            scim()
                .withProperty("camunda.scim.sources[0].id", "one")
                .withProperty("camunda.scim.sources[1].id", "two"));

    // when / then
    assertThat(sources.sourceOfRegistration("a")).isNull();
  }

  @Test
  void shouldAlwaysReplaceTheLoginSourceInClaims() {
    // given
    final Map<String, Object> claims =
        Map.of("sub", "a", LoginSources.LOGIN_SOURCE_CLAIM, "forged");

    // when
    final var replaced = LoginSources.withLoginSource(claims, "entra");
    final var removed = LoginSources.withLoginSource(claims, null);

    // then
    assertThat(LoginSources.loginSource(replaced)).isEqualTo("entra");
    assertThat(removed).containsOnlyKeys("sub");
    assertThat(LoginSources.withoutLoginSource(replaced)).containsOnlyKeys("sub");
  }
}
