/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.secretstore.file.FileBasedSecretStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class SecretStoreConfigurationTest {

  private static final SecretStoreConfiguration CONFIG = new SecretStoreConfiguration();

  @Test
  void shouldFallbackToNoopStoreForDefaultTenantWhenNoStoresConfigured() {
    // given
    final var resolver = resolverFor(Map.of());

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver);

    // then
    assertThat(registries).containsKey(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey("default");
    assertThat(registry.getStores().get("default")).isInstanceOf(NoopSecretStore.class);
  }

  @Test
  void shouldBuildFileBasedStoreWhenFileStoreConfigured() {
    // given
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.main.path", "/etc/camunda/secrets"));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver);

    // then
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey("main");
    assertThat(registry.getStores().get("main")).isInstanceOf(FileBasedSecretStore.class);
  }

  @Test
  void shouldThrowWhenFileStoreHasBlankPath() {
    // given
    final var resolver = resolverFor(Map.of("camunda.secrets.stores.file.bad.path", ""));

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> CONFIG.secretStoreRegistries(resolver))
        .withMessageContaining("bad")
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldThrowWhenMoreThanOneStoreConfigured() {
    // given two file stores for the default tenant
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.store-a.path", "/etc/camunda/secrets-a",
                "camunda.secrets.stores.file.store-b.path", "/etc/camunda/secrets-b"));

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> CONFIG.secretStoreRegistries(resolver))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("only one is supported");
  }

  @Test
  void shouldNormalizeStoreIdToLowercase() {
    // given
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.MyStore.path", "/etc/camunda/secrets"));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver);

    // then
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsOnlyKeys("mystore");
  }

  @Test
  void shouldNotAddNoopStoreWhenStoresAreConfigured() {
    // given
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.main.path", "/etc/camunda/secrets"));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver);

    // then
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsOnlyKeys("main");
    assertThat(registry.getStores()).doesNotContainKey("default");
  }

  @Test
  void shouldFallbackToNoopStoreForNonDefaultTenantWhenNoStoresConfigured() {
    // given
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.physical-tenants.mytenant.security.initialization.default-roles.admin.users[0]",
                "mytenant-admin",
                "camunda.physical-tenants.mytenant.data.secondary-storage.elasticsearch.index-prefix",
                "mytenant"));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver);

    // then
    assertThat(registries).containsKey("mytenant");
    final var registry = registries.get("mytenant");
    assertThat(registry.getStores()).containsKey("default");
    assertThat(registry.getStores().get("default")).isInstanceOf(NoopSecretStore.class);
  }

  @Test
  void shouldProduceOneRegistryPerPhysicalTenant() {
    // given two tenants with different store configurations
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.physical-tenants.tenanta.secrets.stores.file.main.path",
                "/etc/tenanta/secrets",
                "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                "tenanta-admin",
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
                "camunda.physical-tenants.tenantb.secrets.stores.file.other.path",
                "/etc/tenantb/secrets",
                "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
                "tenantb-admin",
                "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver);

    // then each tenant has its own registry with its own store
    assertThat(registries).containsKeys("tenanta", "tenantb");
    assertThat(registries.get("tenanta").getStores()).containsKey("main");
    assertThat(registries.get("tenantb").getStores()).containsKey("other");
  }

  @Test
  void shouldFailFastWhenAwsSecretsManagerStoreHasNoReachableCredentials() {
    // given — AwsSecretsManagerSecretStore.fromConfig() validates connectivity/credentials
    // eagerly, so wiring an aws-secrets-manager store outside a real AWS/LocalStack environment
    // fails fast here rather than constructing successfully. The happy-path construction (real
    // credentials, real batching) is covered at the integration level by
    // AwsSecretsManagerSecretStoreIT, which runs against LocalStack.
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.aws.aws-main.region", "eu-west-1",
                "camunda.secrets.stores.aws.aws-main.path-prefix", "camunda/"));

    // when / then
    assertThatThrownBy(() -> CONFIG.secretStoreRegistries(resolver))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowWhenFileAndAwsSecretsManagerStoresCombinedExceedOne() {
    // given one file store and one aws store for the same tenant
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.file-store.path", "/etc/camunda/secrets",
                "camunda.secrets.stores.aws.aws-store.region", "eu-west-1"));

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> CONFIG.secretStoreRegistries(resolver))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("only one is supported");
  }

  private static PhysicalTenantResolver resolverFor(final Map<String, Object> properties) {
    final var env = new MockEnvironment();
    if (!properties.isEmpty()) {
      env.getPropertySources().addFirst(new MapPropertySource("test", properties));
    }
    final var camunda = new Camunda();
    Binder.get(env).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(env, camunda);
  }
}
