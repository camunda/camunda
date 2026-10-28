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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class PhysicalTenantCredentialGuardTest {

  @Test
  void shouldAcceptAConfigurationWithoutCredentials() {
    // given
    final var tenantConfig = new Camunda();

    // when / then
    assertThatCode(() -> PhysicalTenantCredentialGuard.validate("tenanta", tenantConfig, false))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectALiteralPasswordWithoutNamingIt() {
    // given
    final var tenantConfig =
        tenantConfig(Map.of("data.secondary-storage.rdbms.password", "hunter2"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenantb", tenantConfig, true))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Physical tenant 'tenantb'")
        .hasMessageContaining("data.secondary-storage.rdbms.password")
        .hasMessageNotContaining("hunter2");
  }

  @Test
  void shouldAcceptAReferenceWhenTheTenantHasAnInternalConfigStore() {
    // given
    final var tenantConfig =
        tenantConfig(
            Map.of("data.secondary-storage.rdbms.password", "camunda.secrets.tenantc-db-password"));

    // when / then
    assertThatCode(() -> PhysicalTenantCredentialGuard.validate("tenantc", tenantConfig, true))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectAReferenceWhenTheTenantHasNoInternalConfigStore() {
    // given
    final var tenantConfig =
        tenantConfig(
            Map.of("data.secondary-storage.rdbms.password", "camunda.secrets.tenantd-db-password"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenantd", tenantConfig, false))
        .hasMessageContaining(
            "data.secondary-storage.rdbms.password (a reference, but no internal-config secret"
                + " store)");
  }

  @Test
  void shouldRejectALiteralCredentialOfAClassOutsideThisModule() {
    // given
    final var tenantConfig =
        tenantConfig(Map.of("security.authentication.oidc.client-secret", "oidc-secret"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenante", tenantConfig, true))
        .hasMessageContaining("security.authentication.oidc.client-secret")
        .hasMessageNotContaining("oidc-secret");
  }

  @Test
  void shouldRejectAUrlWithAPassword() {
    // given
    final var tenantConfig =
        tenantConfig(
            Map.of("data.secondary-storage.elasticsearch.url", "https://elastic:changeme@es:9200"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenantf", tenantConfig, true))
        .hasMessageContaining("data.secondary-storage.elasticsearch.url (a URL with a password)")
        .hasMessageNotContaining("changeme");
  }

  @Test
  void shouldAcceptAUrlWithAUserButNoPassword() {
    // given
    final var tenantConfig =
        tenantConfig(Map.of("data.secondary-storage.elasticsearch.url", "https://elastic@es:9200"));

    // when / then
    assertThatCode(() -> PhysicalTenantCredentialGuard.validate("tenantg", tenantConfig, true))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectAPasswordInAConnectionString() {
    // given
    final var tenantConfig =
        tenantConfig(
            Map.of(
                "data.secondary-storage.rdbms.url",
                "jdbc:postgresql://db:5432/camunda?user=camunda&password=hunter2"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenanth", tenantConfig, true))
        .hasMessageContaining(
            "data.secondary-storage.rdbms.url (a password in a connection string)");
  }

  @Test
  void shouldRejectAPrivateKeyInAFreeFormExporterArgument() {
    // given
    final var tenantConfig =
        tenantConfig(
            Map.of(
                "data.exporters.custom.class-name",
                "com.acme.Exporter",
                "data.exporters.custom.args.tls.key",
                "-----BEGIN PRIVATE KEY-----\nMIIEvQ\n-----END PRIVATE KEY-----"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenanti", tenantConfig, true))
        .hasMessageContaining("data.exporters.custom.args.tls.key (a private key)");
  }

  @Test
  void shouldNameEveryOffendingProperty() {
    // given
    final var tenantConfig =
        tenantConfig(
            Map.of(
                "data.secondary-storage.rdbms.password", "one",
                "data.primary-storage.backup.s3.secret-key", "two"));

    // when / then
    assertThatThrownBy(() -> PhysicalTenantCredentialGuard.validate("tenantj", tenantConfig, true))
        .hasMessageContaining("data.secondary-storage.rdbms.password")
        .hasMessageContaining("data.primary-storage.backup.s3.secret-key");
  }

  private static Camunda tenantConfig(final Map<String, Object> properties) {
    final var environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("tenant", prefixed(properties)));
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return camunda;
  }

  private static Map<String, Object> prefixed(final Map<String, Object> properties) {
    final var prefixed = new LinkedHashMap<String, Object>();
    properties.forEach((key, value) -> prefixed.put(Camunda.PREFIX + "." + key, value));
    return prefixed;
  }
}
