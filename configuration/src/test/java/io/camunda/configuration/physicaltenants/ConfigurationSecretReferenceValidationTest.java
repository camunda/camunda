/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class ConfigurationSecretReferenceValidationTest {

  private static final String STORE = "camunda.secrets.internal-config.file.path";
  private static final String PASSWORD = "camunda.data.secondary-storage.elasticsearch.password";

  @Test
  void shouldAcceptAReferenceInAPropertyThatAcceptsOne() {
    // given
    final var properties = Map.<String, Object>of(STORE, "/etc/secrets", PASSWORD, "secret:es");

    // when / then
    assertThatCode(() -> resolve(properties)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectAMalformedReference() {
    // given
    final var properties = Map.<String, Object>of(STORE, "/etc/secrets", PASSWORD, "secret:es.pw");

    // when / then
    assertThatThrownBy(() -> resolve(properties))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Physical tenant 'default'")
        .hasMessageContaining("data.secondary-storage.elasticsearch.password holds a malformed");
  }

  @Test
  void shouldRejectAReferenceInAPropertyThatDoesntAcceptOne() {
    // given
    final var properties =
        Map.<String, Object>of(
            STORE,
            "/etc/secrets",
            "camunda.data.secondary-storage.elasticsearch.cluster-name",
            "secret:cluster");

    // when / then
    assertThatThrownBy(() -> resolve(properties))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("data.secondary-storage.elasticsearch.cluster-name doesn't accept");
  }

  @Test
  void shouldRejectAReferenceInAnExporterArgument() {
    // given
    final var properties =
        Map.<String, Object>of(
            STORE,
            "/etc/secrets",
            "camunda.data.exporters.custom.class-name",
            "com.acme.Exporter",
            "camunda.data.exporters.custom.args.password",
            "secret:custom");

    // when / then
    assertThatThrownBy(() -> resolve(properties))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("data.exporters.custom.args.password doesn't accept");
  }

  @Test
  void shouldRejectAReferenceInTheOidcClientSecret() {
    // given
    final var properties =
        Map.<String, Object>of(
            STORE,
            "/etc/secrets",
            "camunda.security.authentication.oidc.client-secret",
            "secret:oidc");

    // when / then
    assertThatThrownBy(() -> resolve(properties))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("security.authentication.oidc.client-secret doesn't accept");
  }

  @Test
  void shouldRejectAReferenceOfATenantWithoutAConfigurationSecretsStore() {
    // given
    final var properties =
        Map.<String, Object>of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.password",
            "secret:es");

    // when / then
    assertThatThrownBy(() -> resolve(properties))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Physical tenant 'tenanta'")
        .hasMessageContaining("no configuration secrets store");
  }

  private static void resolve(final Map<String, Object> properties) {
    final var all = new HashMap<>(properties);
    if (properties.keySet().stream().anyMatch(key -> key.contains(".tenanta."))) {
      all.put(
          "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
          "tenanta-admin");
      all.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
    }
    final var environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    PhysicalTenantResolver.of(environment, camunda);
  }
}
