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
import io.camunda.configuration.Secrets;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.CaffeineSecretCache;
import io.camunda.secretstore.LocallyCachedSecretStore;
import io.camunda.secretstore.SecretCacheMetricsDoc;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheKeyNames;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheResult;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.secretstore.aws.AwsSecretsManagerSecretStore;
import io.camunda.secretstore.file.FileBasedSecretStore;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.shared.management.ActorClockEndpoint;
import io.camunda.zeebe.shared.management.ActorClockService;
import io.camunda.zeebe.shared.management.ControlledActorClockService;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class SecretStoreConfigurationTest {

  private static final SecretStoreConfiguration CONFIG = new SecretStoreConfiguration();
  private static final ActorClockService CLOCK_SERVICE = System::currentTimeMillis;

  @Test
  void shouldFallbackToNoopStoreForDefaultTenantWhenNoStoresConfigured() {
    // given
    final var resolver = resolverFor(Map.of());

    // when
    final var registries = registries(resolver).byPhysicalTenant();

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
        resolverFor(Map.of("camunda.secrets.stores.file.default.path", secretsDir.toString()));

    // when
    final var registries = registries(resolver).byPhysicalTenant();

    // then the configured directory is what the store reads
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey(SecretStoreRegistry.DEFAULT_STORE_ID);
    final var store = registry.getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(store.list()).containsExactly("token");
    assertThat(store.resolve(Set.of("token"))).containsEntry("token", new Resolved("token-value"));
  }

  @Test
  void shouldThrowWhenFileStoreHasBlankPath() {
    // given a store under the only supported id, so the store id rule passes and the path is what
    // the configuration is rejected for
    final var resolver = resolverFor(Map.of("camunda.secrets.stores.file.default.path", ""));

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> registries(resolver))
        .withMessageContaining("no path configured")
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
        .isThrownBy(() -> registries(resolver))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("only one is supported");
  }

  @Test
  void shouldNormalizeStoreIdToLowercase() {
    // given
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.Default.path", "/etc/camunda/secrets"));

    // when
    final var registries = registries(resolver).byPhysicalTenant();

    // then
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsOnlyKeys(SecretStoreRegistry.DEFAULT_STORE_ID);
  }

  @Test
  void shouldThrowWhenConfiguredStoreIsNotNamedDefault() {
    // given a single store under an id no secret reference can address
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.main.path", "/etc/camunda/secrets"));

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> registries(resolver))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("main")
        .withMessageContaining("the only supported store id is 'default'")
        // the full property the operator has to change, type segment included, so it can be
        // matched against their configuration as it is written there
        .withMessageContaining("camunda.secrets.stores.file.main")
        .withMessageContaining("camunda.secrets.stores.file.default");
  }

  @Test
  void shouldNameTheMisnamedStoreIdAsTheOperatorWroteIt() {
    // given a store id that is not lowercase, and so does not match the id it is registered under
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.MyStore.path", "/etc/camunda/secrets"));

    // when / then — the normalized id appears in no configuration file, so naming it would send
    // the operator looking for a property they never wrote
    assertThatIllegalStateException()
        .isThrownBy(() -> registries(resolver))
        .withMessageContaining("camunda.secrets.stores.file.MyStore")
        .withMessageNotContaining("mystore");
  }

  @Test
  void shouldNameTheTenantScopedPropertyWhenAPhysicalTenantsStoreIsNotNamedDefault() {
    // given a store misnamed under a physical tenant, whose property carries the tenant prefix
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.physical-tenants.tenanta.secrets.stores.file.main.path",
                "/etc/tenanta/secrets",
                "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                "tenanta-admin",
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"));

    // when / then — pointing at camunda.secrets.stores would send the operator to configure a
    // different tenant, leaving this one's store misnamed
    assertThatIllegalStateException()
        .isThrownBy(() -> registries(resolver))
        .withMessageContaining("camunda.physical-tenants.tenanta.secrets.stores.file.default")
        .withMessageNotContaining("rename camunda.secrets.stores");
  }

  @Test
  void shouldRejectStoreUnderAnotherIdBeforeBuildingIt() {
    // given an aws store under an id no secret reference can address. Its construction eagerly
    // builds a client and probes credentials, so building it first would surface an unrelated aws
    // error for a configuration that is rejected either way.
    try (var construction = mockConstruction(AwsSecretsManagerSecretStore.class)) {
      final var resolver =
          resolverFor(Map.of("camunda.secrets.stores.aws.aws-main.region", "eu-west-1"));

      // when / then
      assertThatIllegalStateException()
          .isThrownBy(() -> registries(resolver))
          .withMessageContaining("the only supported store id is 'default'");

      // then — nothing was built, so nothing had to be rolled back
      assertThat(construction.constructed()).isEmpty();
    }
  }

  @Test
  void shouldNotAddNoopStoreWhenStoresAreConfigured(@TempDir final Path secretsDir)
      throws IOException {
    // given — the configured store and the noop fallback share the id 'default', so the only way to
    // tell them apart is by what the registered store reads
    Files.writeString(secretsDir.resolve("token"), "token-value");
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.default.path", secretsDir.toString()));

    // when
    final var registries = registries(resolver).byPhysicalTenant();

    // then the configured store stands, rather than being replaced by the noop fallback
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsOnlyKeys(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(registry.getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID).list())
        .containsExactly("token");
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
    final var registries = registries(resolver).byPhysicalTenant();

    // then
    assertThat(registries).containsKey("mytenant");
    final var registry = registries.get("mytenant");
    assertThat(registry.getStores()).containsKey("default");
    assertThatIsNoopStore(registry.getStores().get("default"));
  }

  @Test
  void shouldProduceOneRegistryPerPhysicalTenant(@TempDir final Path secretsRoot)
      throws IOException {
    // given two tenants each with their own store — both under the id 'default', the only supported
    // one, so the stores are told apart by the directory each reads
    final var tenantASecrets = Files.createDirectory(secretsRoot.resolve("tenanta"));
    Files.writeString(tenantASecrets.resolve("tenanta-token"), "a");
    final var tenantBSecrets = Files.createDirectory(secretsRoot.resolve("tenantb"));
    Files.writeString(tenantBSecrets.resolve("tenantb-token"), "b");
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
                tenantASecrets.toString(),
                "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                "tenanta-admin",
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
                "camunda.physical-tenants.tenantb.secrets.stores.file.default.path",
                tenantBSecrets.toString(),
                "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
                "tenantb-admin",
                "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"));

    // when
    final var registries = registries(resolver).byPhysicalTenant();

    // then each tenant has its own registry with its own store
    assertThat(registries).containsKeys("tenanta", "tenantb");
    assertThat(
            registries.get("tenanta").getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID).list())
        .containsExactly("tenanta-token");
    assertThat(
            registries.get("tenantb").getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID).list())
        .containsExactly("tenantb-token");
  }

  @Test
  void shouldKeepTheCacheMetersOfTwoPhysicalTenantsApart(@TempDir final Path secretsRoot)
      throws IOException {
    // given two tenants whose stores both carry the id 'default', the only supported one
    final var tenantASecrets = Files.createDirectory(secretsRoot.resolve("tenanta"));
    Files.writeString(tenantASecrets.resolve("token"), "a");
    final var tenantBSecrets = Files.createDirectory(secretsRoot.resolve("tenantb"));
    Files.writeString(tenantBSecrets.resolve("token"), "b");
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
                tenantASecrets.toString(),
                "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                "tenanta-admin",
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
                "camunda.physical-tenants.tenantb.secrets.stores.file.default.path",
                tenantBSecrets.toString(),
                "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
                "tenantb-admin",
                "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"));
    // the meter has to survive the same nesting it does in production — a per-tenant wrapped
    // registry inside the composite Spring injects, which forwards to the backend — because
    // MicrometerUtil.wrap does not forward tags more than two levels down. Asserting against a bare
    // leaf registry would leave that hop, and so the collision this test is about, untested
    final var backend = new SimpleMeterRegistry();
    final var clusterRegistry = new CompositeMeterRegistry();
    clusterRegistry.add(backend);
    final var registries =
        CONFIG.secretStoreRegistries(resolver, CLOCK_SERVICE, clusterRegistry).byPhysicalTenant();

    // when only one tenant resolves the name both stores hold
    registries
        .get("tenanta")
        .getStores()
        .get(SecretStoreRegistry.DEFAULT_STORE_ID)
        .resolve(Set.of("token"));

    // then the physical tenant tag tells the two caches apart, all the way down to the registry
    // that would export them — the store id alone cannot, so the two would share one series
    assertThat(cacheMisses(backend, "tenanta")).isOne();
    assertThat(cacheMisses(backend, "tenantb")).isZero();
  }

  @Test
  void shouldPublishNothingForATenantLeftOnTheNoopStore() {
    // given a tenant with no store configured at all, so it falls back to the noop store
    final var resolver = resolverFor(Map.of());
    final var meterRegistry = new SimpleMeterRegistry();
    final var registries =
        CONFIG.secretStoreRegistries(resolver, CLOCK_SERVICE, meterRegistry).byPhysicalTenant();

    // when it is resolved through
    registries
        .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .getStores()
        .get(SecretStoreRegistry.DEFAULT_STORE_ID)
        .resolve(Set.of("token"));

    // then no cache meter exists for it: the noop store caches nothing, so its hit rate would read
    // 0% forever against no TTL or maximum an operator could change
    assertThat(
            meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .filter(name -> name.startsWith("camunda.secret.cache.")))
        .isEmpty();
  }

  private static double cacheMisses(
      final MeterRegistry meterRegistry, final String physicalTenantId) {
    return meterRegistry
        .get(SecretCacheMetricsDoc.CACHE_RESULT.getName())
        .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
        .tag(SecretCacheKeyNames.STORE.asString(), SecretStoreRegistry.DEFAULT_STORE_ID)
        .tag(SecretCacheKeyNames.RESULT.asString(), SecretCacheResult.MISS.name())
        .counter()
        .count();
  }

  @Test
  void shouldBuildAwsSecretsManagerStoreEvenWithoutReachableCredentials() {
    // given — AwsSecretsManagerSecretStore.fromConfig() only probes connectivity/credentials
    // best-effort, logging a warning rather than failing, so wiring an aws-secrets-manager store
    // outside a real AWS/LocalStack environment still constructs successfully; the connectivity
    // error would only surface on first use. That is also why this asserts which store answers
    // rather than what it reads, as every read here would go to AWS. Real resolution against
    // credentials is covered at the integration level by AwsSecretsManagerSecretStoreIT, which runs
    // against LocalStack.
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.aws.default.region", "eu-west-1",
                "camunda.secrets.stores.aws.default.path-prefix", "camunda/"));

    // when
    final var registries = registries(resolver).byPhysicalTenant();

    // then the aws branch is what built the store, not the noop fallback or another store type
    final var registry = registries.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.getStores()).containsKey(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(
            registry
                .getStores()
                .get(SecretStoreRegistry.DEFAULT_STORE_ID)
                .is(AwsSecretsManagerSecretStore.class))
        .isTrue();
  }

  @Test
  void shouldExpireACachedSecretWhenTheClockEndpointTravelsForward(@TempDir final Path secretsDir)
      throws IOException {
    // given a file store, resolved once so the value is cached
    Files.writeString(secretsDir.resolve("token"), "token-value");
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.default.path", secretsDir.toString()));
    final var clockService = new ControlledActorClockService(new ControlledActorClock());
    final var registries =
        CONFIG
            .secretStoreRegistries(resolver, clockService, new SimpleMeterRegistry())
            .byPhysicalTenant();
    final var store =
        registries
            .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .getStores()
            .get(SecretStoreRegistry.DEFAULT_STORE_ID);
    store.resolve(Set.of("token"));
    assertThat(store.lookupLocal("token")).contains("token-value");

    // when the /actuator/clock endpoint travels forward past the cache's TTL — driven through the
    // real endpoint rather than the mutable clock directly, since ControlledActorClock only
    // reflects a mutation once update() runs, which is exactly what the endpoint does for every
    // write; adding (never pinning) keeps the ticker moving forward relative to the write
    // timestamp already recorded for the cached entry
    final var response =
        new ActorClockEndpoint(clockService)
            .modify("add", null, CaffeineSecretCache.DEFAULT_TTL.plusMinutes(1).toMillis());
    assertThat(response.getStatus()).isEqualTo(200);

    // then the cached secret has expired
    assertThat(store.lookupLocal("token")).isEmpty();
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
        .isThrownBy(() -> registries(resolver))
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
        .isThrownBy(() -> registries(resolver))
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
                  "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
                  "/etc/tenanta/secrets",
                  "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                  "tenanta-admin",
                  "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                  "tenanta",
                  "camunda.physical-tenants.tenantb.secrets.stores.file.default.path",
                  "/etc/tenantb/secrets",
                  "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
                  "tenantb-admin",
                  "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                  "tenantb"));
      final var meterRegistry = new SimpleMeterRegistry();

      // when / then — the build fails and the failure propagates (Mockito wraps the construction
      // failure, but it is still a RuntimeException the rollback path catches)
      assertThatThrownBy(() -> CONFIG.secretStoreRegistries(resolver, CLOCK_SERVICE, meterRegistry))
          .isInstanceOf(RuntimeException.class)
          .hasRootCauseInstanceOf(IllegalStateException.class);

      // then — the first tenant's already-built store is rolled back (closed) so its client does
      // not leak
      verify(construction.constructed().get(0)).close();
      // and so is the registry its cache meters were published on: a failed startup that left them
      // behind would keep exporting the hit rate and size of a cache nothing resolves through
      assertThat(
              meterRegistry.getMeters().stream()
                  .map(meter -> meter.getId().getName())
                  .filter(name -> name.startsWith("camunda.secret.cache.")))
          .isEmpty();
    }
  }

  @Test
  void shouldExpireACachedSecretAfterAConfiguredTtlShorterThanTheDefault(
      @TempDir final Path secretsDir) throws IOException {
    // given a file store with a one-minute cache ttl, resolved once so the value is cached
    Files.writeString(secretsDir.resolve("token"), "token-value");
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secretsDir.toString(),
                "camunda.secrets.cache.ttl",
                "1m"));
    final var clockService = new ControlledActorClockService(new ControlledActorClock());
    final var store = defaultTenantStore(resolver, clockService);
    store.resolve(Set.of("token"));
    assertThat(store.lookupLocal("token")).contains("token-value");

    // when the clock travels forward two minutes — past the configured ttl but well short of the
    // 20-minute default, which would still serve the value
    travelForward(clockService, Duration.ofMinutes(2));

    // then the configured ttl is what governed the entry, so it has expired
    assertThat(store.lookupLocal("token")).isEmpty();
  }

  @Test
  void shouldServeACachedSecretPastTheDefaultTtlWhenTheConfiguredTtlIsLonger(
      @TempDir final Path secretsDir) throws IOException {
    // given a file store with a thirty-minute cache ttl, resolved once so the value is cached
    Files.writeString(secretsDir.resolve("token"), "token-value");
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secretsDir.toString(),
                "camunda.secrets.cache.ttl",
                "30m"));
    final var clockService = new ControlledActorClockService(new ControlledActorClock());
    final var store = defaultTenantStore(resolver, clockService);
    store.resolve(Set.of("token"));

    // when the clock travels forward past the 20-minute default but not past the configured ttl
    travelForward(clockService, Duration.ofMinutes(21));

    // then the value is still cached — together with the shorter-ttl test this rules out the
    // configured value being read but the default silently applied, in either direction
    assertThat(store.lookupLocal("token")).contains("token-value");
  }

  @Test
  void shouldUseThePhysicalTenantsOwnCacheTtl(@TempDir final Path secretsDir) throws IOException {
    // given two tenants, each with its own file store, and a tenant that shortens the root ttl
    final var tenantaDir = Files.createDirectory(secretsDir.resolve("tenanta"));
    final var tenantbDir = Files.createDirectory(secretsDir.resolve("tenantb"));
    Files.writeString(tenantaDir.resolve("token"), "tenanta-value");
    Files.writeString(tenantbDir.resolve("token"), "tenantb-value");
    final var resolver =
        resolverFor(
            Map.ofEntries(
                Map.entry("camunda.secrets.cache.ttl", "30m"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
                    tenantaDir.toString()),
                Map.entry("camunda.physical-tenants.tenanta.secrets.cache.ttl", "1m"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                    "tenanta-admin"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                    "tenanta"),
                Map.entry(
                    "camunda.physical-tenants.tenantb.secrets.stores.file.default.path",
                    tenantbDir.toString()),
                Map.entry(
                    "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
                    "tenantb-admin"),
                Map.entry(
                    "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                    "tenantb")));
    final var clockService = new ControlledActorClockService(new ControlledActorClock());
    final var registries =
        CONFIG
            .secretStoreRegistries(resolver, clockService, new SimpleMeterRegistry())
            .byPhysicalTenant();
    final var tenanta =
        registries.get("tenanta").getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID);
    final var tenantb =
        registries.get("tenantb").getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID);
    tenanta.resolve(Set.of("token"));
    tenantb.resolve(Set.of("token"));

    // when the clock travels forward past tenanta's ttl and past the 20-minute default, but short
    // of tenantb's inherited 30 minutes — anything under 20 would leave tenantb cached whether it
    // inherited the root ttl or silently fell back to the default
    travelForward(clockService, Duration.ofMinutes(21));

    // then only tenanta's entry expired, so both the per-tenant override and the root value it
    // overrides reached the registry rather than stopping at the configuration object
    assertThat(tenanta.lookupLocal("token")).isEmpty();
    assertThat(tenantb.lookupLocal("token")).contains("tenantb-value");
  }

  @Test
  void shouldBoundEachStoresCacheByTheConfiguredMaxSize(@TempDir final Path secretsDir)
      throws IOException {
    // given a store holding three secrets and a cache configured to hold one
    for (final String name : Set.of("first", "second", "third")) {
      Files.writeString(secretsDir.resolve(name), name + "-value");
    }
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secretsDir.toString(),
                "camunda.secrets.cache.max-size",
                "1"));
    final var store = defaultTenantStore(resolver, CLOCK_SERVICE);

    // when all three are resolved
    store.resolve(Set.of("first", "second", "third"));

    // then exactly the configured maximum is left cached — one, not zero, so a cache that stopped
    // holding anything at all would fail this too. Asserted eventually because eviction is
    // asynchronous and the cache's own cleanUp() is not reachable through the registry; once it has
    // run, the count is deterministic.
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        Stream.of("first", "second", "third")
                            .filter(name -> store.lookupLocal(name).isPresent())
                            .count())
                    .isEqualTo(1));
  }

  @Test
  void shouldFailToBuildRegistriesWhenTheCacheTtlIsBelowOneMinute() {
    // given a sub-minute cache ttl and no stores configured at all
    final var resolver = resolverFor(Map.of("camunda.secrets.cache.ttl", "30s"));

    // when / then the registries fail to build, naming the offending property — so the check runs
    // at startup, and on the noop-fallback path too
    assertThatThrownBy(() -> registries(resolver))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("camunda.secrets.cache.ttl");
  }

  @Test
  void shouldFailToBuildRegistriesWhenTheCacheMaxSizeIsBelowOne() {
    // given a max size below 1 and no stores configured at all
    final var resolver = resolverFor(Map.of("camunda.secrets.cache.max-size", "0"));

    // when / then the registries fail to build, naming the offending property — the same startup
    // check the ttl gets, rather than max-size being validated only at the configuration layer
    assertThatThrownBy(() -> registries(resolver))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("camunda.secrets.cache.max-size");
  }

  @Test
  void shouldNameThePhysicalTenantWhoseCacheConfigurationIsInvalid() {
    // given two tenants, only one of which overrides the cache ttl with a sub-minute value
    final var resolver =
        resolverFor(
            Map.ofEntries(
                Map.entry("camunda.physical-tenants.tenanta.secrets.cache.ttl", "30s"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                    "tenanta-admin"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                    "tenanta"),
                Map.entry(
                    "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
                    "tenantb-admin"),
                Map.entry(
                    "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                    "tenantb")));

    // when / then the failure names the tenant as well as the property — the config object reports
    // the canonical path whichever tenant it came from, so without the tenant an operator running
    // several cannot tell whose override is at fault
    assertThatThrownBy(() -> registries(resolver))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenanta")
        .hasMessageContaining("camunda.secrets.cache.ttl");
  }

  @Test
  void shouldFailToBuildRegistriesWhenMaxConcurrencyIsBelowOne() {
    // given a max-concurrency of 0 and no stores configured at all
    final var resolver = resolverFor(Map.of("camunda.secrets.max-concurrency", "0"));

    // when / then the registries fail to build, naming the offending property
    assertThatThrownBy(() -> registries(resolver))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("camunda.secrets.max-concurrency");
  }

  @Test
  void shouldResolveEveryNameCorrectlyThroughTheConcurrencyWrapper(@TempDir final Path secretsDir)
      throws IOException {
    // given a file store (a one-by-one store) holding several secrets, at the default
    // max-concurrency, so resolving all of them fans out across chunks
    for (var i = 0; i < 5; i++) {
      Files.writeString(secretsDir.resolve("secret-" + i), "value-" + i);
    }
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.default.path", secretsDir.toString()));
    final var store =
        registries(resolver)
            .byPhysicalTenant()
            .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .getStores()
            .get(SecretStoreRegistry.DEFAULT_STORE_ID);

    // when
    final var names = Set.of("secret-0", "secret-1", "secret-2", "secret-3", "secret-4");
    final var results = store.resolveFromStore(names);

    // then every name is answered with its own value; chunking must not mix up which name
    // resolved to which value
    names.forEach(
        name ->
            assertThat(results.get(name))
                .isEqualTo(new Resolved(name.replace("secret-", "value-"))));
  }

  @Test
  void shouldResolveEveryNameCorrectlyWhenMaxConcurrencyIsOne(@TempDir final Path secretsDir)
      throws IOException {
    // given the same store, with concurrency explicitly disabled: today's behavior, kept as a
    // regression check
    for (var i = 0; i < 5; i++) {
      Files.writeString(secretsDir.resolve("secret-" + i), "value-" + i);
    }
    final var resolver =
        resolverFor(
            Map.of(
                "camunda.secrets.stores.file.default.path",
                secretsDir.toString(),
                "camunda.secrets.max-concurrency",
                "1"));
    final var store =
        registries(resolver)
            .byPhysicalTenant()
            .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .getStores()
            .get(SecretStoreRegistry.DEFAULT_STORE_ID);

    // when
    final var names = Set.of("secret-0", "secret-1", "secret-2", "secret-3", "secret-4");
    final var results = store.resolveFromStore(names);

    // then
    names.forEach(
        name ->
            assertThat(results.get(name))
                .isEqualTo(new Resolved(name.replace("secret-", "value-"))));
  }

  @Test
  void shouldStillIdentifyStoreTypeThroughTheConcurrencyWrapper(@TempDir final Path secretsDir) {
    // given a file store, wrapped for concurrency at the default max-concurrency
    final var resolver =
        resolverFor(Map.of("camunda.secrets.stores.file.default.path", secretsDir.toString()));
    final var store =
        registries(resolver)
            .byPhysicalTenant()
            .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .getStores()
            .get(SecretStoreRegistry.DEFAULT_STORE_ID);

    // then a caller asking which store was configured gets the same answer whether or not
    // concurrency wrapped it, exactly as caching already guarantees
    assertThat(store.is(FileBasedSecretStore.class)).isTrue();
  }

  @Test
  void shouldDefaultToTheProductionCacheDefaults() {
    // given the configuration module restates the cache defaults as literals, since it does not
    // depend on secret-store-api
    // when the unconfigured defaults are read
    final var cache = new Secrets().getCache();

    // then they match the cache implementation's own defaults; dist is the only module that sees
    // both, so this is where the two can be pinned together
    assertThat(cache.getTtl()).isEqualTo(CaffeineSecretCache.DEFAULT_TTL);
    assertThat(cache.getMaxSize()).isEqualTo(CaffeineSecretCache.DEFAULT_MAX_SIZE);
  }

  /**
   * Travels the clock forward through the real {@code /actuator/clock} endpoint rather than
   * mutating the clock directly, since {@link ControlledActorClock} only reflects a mutation once
   * update() runs, which is exactly what the endpoint does for every write. Adding (never pinning)
   * keeps the ticker moving forward relative to the write timestamp already recorded for a cached
   * entry.
   */
  private static void travelForward(
      final ControlledActorClockService clockService, final Duration offset) {
    final var response =
        new ActorClockEndpoint(clockService).modify("add", null, offset.toMillis());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private static LocallyCachedSecretStore defaultTenantStore(
      final PhysicalTenantResolver resolver, final ActorClockService clockService) {
    return CONFIG
        .secretStoreRegistries(resolver, clockService, new SimpleMeterRegistry())
        .byPhysicalTenant()
        .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .getStores()
        .get(SecretStoreRegistry.DEFAULT_STORE_ID);
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

  private static SecretStoreRegistries registries(final PhysicalTenantResolver resolver) {
    return CONFIG.secretStoreRegistries(resolver, CLOCK_SERVICE, new SimpleMeterRegistry());
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
