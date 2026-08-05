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
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.CachingSecretStore;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.aws.AwsSecretsManagerSecretStore;
import io.camunda.secretstore.file.FileBasedSecretStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

    // then
    assertThat(registries).containsKey(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey("default");
    assertThatIsNoopStore(registry.getStores().get("default"));
  }

  @Test
  void shouldBuildFileBasedStoreWhenFileStoreConfigured(@TempDir final Path secretsDir)
      throws IOException {
    // given a directory holding one secret, so the store built for it can be told apart by what it
    // reads rather than by its type: the registry hands out a store that caches, not the store the
    // configuration constructed
    Files.writeString(secretsDir.resolve("token"), "token-value");
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.main.path", secretsDir.toString()));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

    // then the configured directory is what the store reads
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey("main");
    final var store = registry.getStores().get("main");
    assertThat(store.list()).containsExactly("token");
    assertThat(store.resolve(Set.of("token"))).containsEntry("token", new Resolved("token-value"));
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
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

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
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

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
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

    // then
    assertThat(registries).containsKey("mytenant");
    final var registry = registries.get("mytenant");
    assertThat(registry.getStores()).containsKey("default");
    assertThatIsNoopStore(registry.getStores().get("default"));
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
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

    // then each tenant has its own registry with its own store
    assertThat(registries).containsKeys("tenanta", "tenantb");
    assertThat(registries.get("tenanta").getStores()).containsKey("main");
    assertThat(registries.get("tenantb").getStores()).containsKey("other");
  }

  @Test
  void shouldBuildAwsSecretsManagerStoreEvenWithoutReachableCredentials() {
    // given — AwsSecretsManagerSecretStore.fromConfig() only probes connectivity/credentials
    // best-effort, logging a warning rather than failing, so wiring an aws-secrets-manager store
    // outside a real AWS/LocalStack environment still constructs successfully; the connectivity
    // error would only surface on first use. That is also why this asserts which store was wrapped
    // rather than what it reads, as every read here would go to AWS. Real resolution against
    // credentials is covered at the integration level by AwsSecretsManagerSecretStoreIT, which runs
    // against LocalStack.
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.aws.aws-main.region", "eu-west-1",
                "camunda.secrets.stores.aws.aws-main.path-prefix", "camunda/"));

    // when
    final var registries = CONFIG.secretStoreRegistries(resolver).byPhysicalTenant();

    // then the aws branch is what built the store, not the noop fallback or another store type
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey("aws-main");
    assertThat(registry.getStores().get("aws-main"))
        .isInstanceOfSatisfying(
            CachingSecretStore.class,
            store -> assertThat(store.wraps(AwsSecretsManagerSecretStore.class)).isTrue());
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

  @Test
  void shouldThrowWhenFileAndGcpStoresCombinedExceedOne() {
    // given one file store and one gcp store for the same tenant
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.file-store.path", "/etc/camunda/secrets",
                "camunda.secrets.stores.gcp.gcp-store.project-id", "my-project"));

    // when / then — the per-tenant cap is enforced before any GCP client is built
    assertThatIllegalStateException()
        .isThrownBy(() -> CONFIG.secretStoreRegistries(resolver))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("only one is supported");
  }

  @Test
  void shouldCloseAlreadyBuiltStoresWhenALaterTenantFailsToBuild() {
    // given two tenants each with a single file store; the second store's construction blows up,
    // simulating a store that fails to build after an earlier tenant's store was already created.
    // Counting constructions (not tenants) keeps this deterministic regardless of tenant iteration
    // order: whichever tenant is processed first builds successfully, the second one fails.
    try (var construction =
        mockConstruction(
            FileBasedSecretStore.class,
            (mock, context) -> {
              if (context.getCount() == 2) {
                throw new IllegalStateException("second tenant's store failed to build");
              }
            })) {
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

      // when / then — the build fails and the failure propagates (Mockito wraps the construction
      // failure, but it is still a RuntimeException the rollback path catches)
      assertThatThrownBy(() -> CONFIG.secretStoreRegistries(resolver))
          .isInstanceOf(RuntimeException.class)
          .hasRootCauseInstanceOf(IllegalStateException.class);

      // then — the first tenant's already-built store is rolled back (closed) so its client does
      // not leak
      verify(construction.constructed().get(0)).close();
    }
  }

  /**
   * Asserts the store is the noop fallback by what it answers: it holds nothing and reports every
   * name as missing with the fallback's own message.
   */
  private static void assertThatIsNoopStore(final SecretStore store) {
    assertThat(store.list()).isEmpty();
    assertThat(store.resolve(Set.of("token")))
        .containsEntry(
            "token", new Failed(SecretErrorCode.NOT_FOUND, "No secret store configured", null));
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
