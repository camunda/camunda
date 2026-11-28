/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.file.FileBasedSecretStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationSecretStoresTest {

  private static final SecretStoreConfiguration CONFIG = new SecretStoreConfiguration();

  @TempDir private Path secrets;

  @Test
  void shouldBuildTheConfigurationSecretsStoreOfEachTenant() throws IOException {
    // given
    Files.createDirectories(secrets.resolve("root"));
    Files.createDirectories(secrets.resolve("tenanta"));
    Files.writeString(secrets.resolve("root/es-password"), "root-value");
    Files.writeString(secrets.resolve("tenanta/es-password"), "tenanta-value");
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("root").toString(),
                "camunda.physical-tenants.tenanta.secrets.internal-config.file.path",
                secrets.resolve("tenanta").toString()));

    // when
    try (var stores = ConfigurationSecretStores.of(resolver)) {

      // then
      assertThat(resolve(stores, "default", "es-password")).isEqualTo("root-value");
      assertThat(resolve(stores, "tenanta", "es-password")).isEqualTo("tenanta-value");
    }
  }

  @Test
  void shouldHaveNoConfigurationSecretsStoreWhenNoneIsConfigured() {
    // given
    final var resolver = resolverFor(Map.of());

    // when
    try (var stores = ConfigurationSecretStores.of(resolver)) {

      // then
      assertThat(stores.forPhysicalTenant("default")).isEmpty();
    }
  }

  @Test
  void shouldNotHandTheConfigurationSecretsStoreToJobs() throws IOException {
    // given
    Files.writeString(secrets.resolve("es-password"), "value");
    final var resolver =
        resolverFor(Map.of("camunda.secrets.internal-config.file.path", secrets.toString()));

    // when
    try (var registries =
        CONFIG.secretStoreRegistries(
            resolver, System::currentTimeMillis, new SimpleMeterRegistry())) {

      // then
      assertThat(registries.forPhysicalTenant("default").getStores().values())
          .allSatisfy(
              store ->
                  assertThat(store.resolve(Set.of("es-password")))
                      .doesNotContainValue(new Resolved("value")));
    }
  }

  @Test
  void shouldRejectMoreThanOneConfigurationSecretsStore() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.internal-config.file.path",
                secrets.toString(),
                "camunda.secrets.internal-config.env.prefix",
                "CONFIG_SECRET_"));

    // when / then
    assertThatThrownBy(() -> ConfigurationSecretStores.of(resolver))
        .hasMessageContaining("Physical tenant 'default'")
        .hasMessageContaining("camunda.secrets.internal-config");
  }

  @Test
  void shouldRejectAConfigurationSecretsStoreWithinTheJobStore() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secrets.toString(),
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("config").toString()));

    // when / then
    assertThatThrownBy(() -> ConfigurationSecretStores.of(resolver))
        .hasMessageContaining("camunda.secrets.internal-config.file")
        .hasMessageContaining("camunda.secrets.stores.file.default");
  }

  @Test
  void shouldNameTheRootPropertiesWhenInheritedStoresOverlap() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secrets.toString(),
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("config").toString(),
                "camunda.physical-tenants.tenanta.secrets.cache.max-size",
                "100"));

    // when / then
    assertThatThrownBy(() -> ConfigurationSecretStores.of(resolver))
        .hasMessageContaining("Physical tenant 'default'")
        .hasMessageNotContaining("tenanta");
  }

  @Test
  void shouldNameTheTenantPropertiesWhenATenantsStoresOverlap() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.physical-tenants.tenanta.secrets.stores.aws.default.path-prefix",
                "camunda/",
                "camunda.physical-tenants.tenanta.secrets.internal-config.aws.path-prefix",
                "camunda/internal/"));

    // when / then
    assertThatThrownBy(() -> ConfigurationSecretStores.of(resolver))
        .hasMessageContaining("Physical tenant 'tenanta'")
        .hasMessageContaining("camunda.physical-tenants.tenanta.secrets.internal-config.aws")
        .hasMessageContaining("camunda.physical-tenants.tenanta.secrets.stores.aws.default");
  }

  @Test
  void shouldRejectAJobStoreOfAnotherTenantThatReachesAConfigurationSecretsStore() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("shared/default-config").toString(),
                "camunda.physical-tenants.tenanta.secrets.internal-config.file.path",
                secrets.resolve("tenanta-config").toString(),
                "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
                secrets.resolve("shared").toString()));

    // when / then
    assertThatThrownBy(() -> ConfigurationSecretStores.of(resolver))
        .hasMessageContaining("Physical tenant 'default'")
        .hasMessageContaining("camunda.secrets.internal-config.file")
        .hasMessageContaining("camunda.physical-tenants.tenanta.secrets.stores.file.default");
  }

  @Test
  void shouldAcceptStoresOfTheSameBackendThatDontOverlap() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secrets.resolve("jobs").toString(),
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("config").toString()));

    // when
    try (var stores = ConfigurationSecretStores.of(resolver)) {

      // then
      assertThat(stores.forPhysicalTenant("default")).isPresent();
    }
  }

  @Test
  void shouldCloseOnlyTheReplacedStoreOfOneTenant() {
    // given
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var next = new HashMap<>(Map.of("tenanta", storeA, "tenantb", storeB));
    final var stores = new ConfigurationSecretStores((tenantId, secrets) -> next.remove(tenantId));
    stores.put("tenanta", new Secrets());
    stores.put("tenantb", new Secrets());
    final var newStoreA = mock(SecretStore.class);
    next.put("tenanta", newStoreA);

    // when
    stores.put("tenanta", new Secrets());

    // then
    verify(storeA).close();
    verify(storeB, never()).close();
    assertThat(stores.forPhysicalTenant("tenanta")).containsSame(newStoreA);
  }

  private static String resolve(
      final ConfigurationSecretStores stores, final String tenantId, final String name) {
    final var store = stores.forPhysicalTenant(tenantId).orElseThrow();
    assertThat(store).isInstanceOf(FileBasedSecretStore.class);
    return ((Resolved) store.resolve(Set.of(name)).get(name)).value();
  }

  private static PhysicalTenantResolver resolverFor(final Map<String, Object> properties) {
    final var all = new HashMap<>(properties);
    if (properties.keySet().stream().anyMatch(key -> key.contains(".tenanta."))) {
      all.put(
          "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
          "tenanta-admin");
      all.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
    }
    final var env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", all));
    final var camunda = new Camunda();
    Binder.get(env).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(env, camunda);
  }
}
