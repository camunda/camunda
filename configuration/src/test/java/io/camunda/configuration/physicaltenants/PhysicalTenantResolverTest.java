/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantResolverTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private PhysicalTenantResolver newResolver() {
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(environment, camunda);
  }

  private void setProperties(final Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
  }

  /**
   * Sets {@code properties} and, for each given non-default tenant, adds the minimal {@code
   * security.initialization} block that {@link PhysicalTenantRequiredOverrideValidation} now
   * requires of every explicitly-configured tenant.
   */
  private void setProperties(final Map<String, Object> properties, final String... tenantIds) {
    final Map<String, Object> all = new HashMap<>(properties);
    for (final String tenantId : tenantIds) {
      all.put(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
  }

  private static String indexPrefixOf(final Camunda camunda) {
    return camunda.getData().getSecondaryStorage().getElasticsearch().getIndexPrefix();
  }

  @Test
  void shouldResolveOneCamundaPerDiscoveredTenant() {
    // given two tenants under the physical-tenants prefix, each overriding an overridable property
    // (the index prefix) with a distinct value so they also pass cross-tenant isolation
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
            "tenantb"),
        "tenanta",
        "tenantb");

    // when
    final Map<String, Camunda> resolved = newResolver().getAll();

    // then both declared tenants are present (alongside the synthesized 'default')
    assertThat(indexPrefixOf(resolved.get("tenanta"))).isEqualTo("tenanta");
    assertThat(indexPrefixOf(resolved.get("tenantb"))).isEqualTo("tenantb");
  }

  @Test
  void shouldSeedTenantWithRootValuesForNonOverriddenProperties() {
    // given root cluster values plus a tenant override on an overridable cluster field
    // (partition-count) and a distinct storage location so it does not collide with 'default'
    setProperties(
        Map.of(
            "camunda.cluster.size", 5,
            "camunda.cluster.replication-factor", 3,
            "camunda.physical-tenants.tenanta.cluster.partition-count", 7,
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then non-overridden cluster fields equal the root, the overridden field equals the tenant
    assertThat(tenantA.getCluster().getSize()).isEqualTo(5);
    assertThat(tenantA.getCluster().getReplicationFactor()).isEqualTo(3);
    assertThat(tenantA.getCluster().getPartitionCount()).isEqualTo(7);
  }

  @Test
  void shouldFallBackToLegacyPropertiesForNonOverriddenProperties() {
    // given a legacy broker property is set, the unified property is not set,
    // and the tenant overrides only an unrelated, overridable field
    setProperties(
        Map.of(
            "zeebe.broker.cluster.partitionsCount",
            9,
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"),
        "tenanta");

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then the legacy fallback applies for the non-overridden property at getter time
    assertThat(tenantA.getCluster().getPartitionCount()).isEqualTo(9);
    assertThat(indexPrefixOf(tenantA)).isEqualTo("tenanta");
  }

  @Test
  void shouldResolveDocumentConfigurationPerTenant() {
    // given root document configuration and a tenant override for one store property
    // (each tenant also gets a distinct index-prefix so they pass cross-tenant isolation)
    setProperties(
        Map.of(
            "camunda.document.default-store-id", "aws1",
            "camunda.document.aws.aws1.bucket-name", "root-bucket",
            "camunda.data.secondary-storage.elasticsearch.index-prefix", "default",
            "camunda.physical-tenants.tenanta.document.aws.aws1.bucket-name", "tenant-bucket",
            "camunda.physical-tenants.tenanta.document.assigned[0]", "aws1",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when
    final PhysicalTenantResolver resolver = newResolver();
    final Camunda defaultTenant =
        resolver.forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    final Camunda tenantA = resolver.forPhysicalTenant("tenanta");

    // then the default tenant keeps root values and tenant configuration is overlaid
    assertThat(defaultTenant.getDocument().getDefaultStoreId()).isEqualTo("aws1");
    assertThat(defaultTenant.getDocument().getAws().get("aws1").getBucketName())
        .isEqualTo("root-bucket");
    assertThat(tenantA.getDocument().getDefaultStoreId()).isEqualTo("aws1");
    assertThat(tenantA.getDocument().getAws().get("aws1").getBucketName())
        .isEqualTo("tenant-bucket");
  }

  @Test
  void shouldKeepFlatOidcSlotWhenProviderOverlayReplacesProvidersSubtree() {
    // given root sets the flat oidc slot AND a named provider the tenant partially overrides —
    // the provider overlay installer must replace only the providers subtree, leaving the
    // two-bind-resolved sibling authentication fields (like the flat slot) untouched
    setProperties(
        Map.of(
            "camunda.security.authentication.oidc.issuer-uri",
                "http://localhost:8081/realms/default",
            "camunda.security.authentication.providers.oidc.shared.issuer-uri",
                "http://localhost:8082/realms/shared",
            "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.shared.client-id",
                "tenanta-client",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then the flat slot survives and the named provider is deep-merged next to it
    final var authentication = tenantA.getSecurity().getAuthentication();
    assertThat(authentication.getOidc().getIssuerUri())
        .isEqualTo("http://localhost:8081/realms/default");
    final var shared = authentication.getProviders().getOidc().get("shared");
    assertThat(shared.getIssuerUri()).isEqualTo("http://localhost:8082/realms/shared");
    assertThat(shared.getClientId()).isEqualTo("tenanta-client");
  }

  @Test
  void shouldKeepRootProvidersWhenTenantOverlayHasEmptyProvidersMap() {
    // given root declares a named provider and the tenant's only providers overlay is an empty
    // map — Spring Boot 4.1 surfaces `providers: {}` as an empty property value, which must mean
    // "nothing to overlay" through the full resolver path (bind, overlay, installer), not a reset
    // of the inherited providers
    setProperties(
        Map.of(
            "camunda.security.authentication.providers.oidc.shared.issuer-uri",
                "http://localhost:8082/realms/shared",
            "camunda.security.authentication.providers.oidc.shared.client-id", "shared-client",
            "camunda.physical-tenants.tenanta.security.authentication.providers", "",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then the inherited provider survives the empty overlay
    final var shared = tenantA.getSecurity().getAuthentication().getProviders().getOidc();
    assertThat(shared).containsKey("shared");
    assertThat(shared.get("shared").getIssuerUri())
        .isEqualTo("http://localhost:8082/realms/shared");
    assertThat(shared.get("shared").getClientId()).isEqualTo("shared-client");
  }

  @Test
  void shouldSynthesizeDefaultTenantFromRootWhenNoTenantsAreDeclared() {
    // given only root configuration is set
    setProperties(Map.of("camunda.cluster.size", 5));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then a default tenant is synthesized from the root so consumers can always look it up
    assertThat(resolver.getAll()).containsOnlyKeys(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getCluster()
                .getSize())
        .isEqualTo(5);
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenPhysicalTenantIdIsNotFound() {
    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then
    assertThatThrownBy(() -> resolver.forPhysicalTenant("missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id 'missing'");
  }

  @Test
  void shouldSynthesizeDefaultTenantFromRootWhenOtherTenantsAreDeclared() {
    // given a root (cluster-wide) value plus a tenant declared with an overridable override and a
    // distinct storage location so it does not collide with the synthesized 'default'
    setProperties(
        Map.of(
            "camunda.cluster.size",
            5,
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"),
        "tenanta");

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then a default tenant is added alongside the declared one, carrying the root values
    assertThat(resolver.getAll())
        .containsOnlyKeys("tenanta", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getCluster()
                .getSize())
        .isEqualTo(5);
    // the declared tenant inherits the root cluster size and carries its own storage override
    assertThat(resolver.forPhysicalTenant("tenanta").getCluster().getSize()).isEqualTo(5);
    assertThat(indexPrefixOf(resolver.forPhysicalTenant("tenanta"))).isEqualTo("tenanta");
  }

  @Test
  void shouldHonorExplicitlyDeclaredDefaultTenantOverrides() {
    // given the user explicitly declares a 'default' tenant with an overridable override
    setProperties(
        Map.of(
            "camunda.physical-tenants.default.data.secondary-storage.elasticsearch.index-prefix",
            "custom"));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then the explicit declaration wins and is not clobbered by synthesis
    assertThat(
            indexPrefixOf(resolver.forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)))
        .isEqualTo("custom");
  }

  @Test
  void shouldRejectTenantsResolvingToTheSameSecondaryStorage() {
    // given two tenants explicitly pointing at the same Elasticsearch url with the same (empty)
    // index prefix — they would write into the same database
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.url",
                "http://shared:9200",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.url",
                "http://shared:9200"),
        "tenanta",
        "tenantb");

    // when / then resolution fails fast at boot
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("secondary-storage location");
  }

  @Test
  void shouldRejectTenantsWithIncompatibleSecondaryStorageTypes() {
    // given one tenant on Elasticsearch and one on RDBMS (incompatible compatibility classes),
    // each with a distinct storage location so the isolation rule passes first
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.type", "elasticsearch",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.type", "rdbms",
            "camunda.physical-tenants.tenantb.data.secondary-storage.rdbms.url",
                "jdbc:h2:mem:tenantb"),
        "tenanta",
        "tenantb");

    // when / then resolution fails fast at boot
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("compatible secondary-storage type");
  }

  @Test
  void shouldResolveWhenTenantsUseDistinctSecondaryStorage() {
    // given two tenants on the same Elasticsearch cluster but with distinct index prefixes
    // (and the synthesized 'default' keeps the empty prefix) — no collision
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"),
        "tenanta",
        "tenantb");

    // when / then resolution succeeds
    final PhysicalTenantResolver resolver = newResolver();
    assertThat(resolver.getAll())
        .containsOnlyKeys("tenanta", "tenantb", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldRejectRetentionEnabledTenantsSharingLifecyclePolicyOnSharedCluster() {
    // given two tenants on the same Elasticsearch cluster with distinct index prefixes (so the
    // isolation rule passes) but both with retention enabled and the default policy name — the
    // cluster-global ILM policy would be overwritten
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenanta.data.secondary-storage.retention.enabled", "true",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb",
            "camunda.physical-tenants.tenantb.data.secondary-storage.retention.enabled", "true"),
        "tenanta",
        "tenantb");

    // when / then resolution fails fast at boot on the shared lifecycle policy
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("lifecycle");
  }

  @Test
  void shouldResolveWhenSharedClusterButDistinctLifecyclePolicyNames() {
    // given two retention-enabled tenants on the same cluster (distinct index prefixes) that give
    // their history lifecycle policies distinct names
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenanta.data.secondary-storage.retention.enabled", "true",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.history.policy-name",
                "tenanta-policy",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.history.usage-metrics-policy-name",
                "tenanta-usage-metrics-policy",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb",
            "camunda.physical-tenants.tenantb.data.secondary-storage.retention.enabled", "true",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.history.policy-name",
                "tenantb-policy",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.history.usage-metrics-policy-name",
                "tenantb-usage-metrics-policy"),
        "tenanta",
        "tenantb");

    // when / then distinct policy names isolate the tenants — resolution succeeds
    final PhysicalTenantResolver resolver = newResolver();
    assertThat(resolver.getAll())
        .containsOnlyKeys("tenanta", "tenantb", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldRejectTenantsInheritingOneRootSnapshotRepository() {
    // given a root repository-name and two tenants that override only their index prefix (so the
    // isolation rule passes) — both inherit the one repository, which is the default outcome and
    // only visible once the root and tenant configurations are resolved together
    setProperties(
        Map.of(
            "camunda.data.secondary-storage.elasticsearch.backup.repository-name", "camunda-backup",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"),
        "tenanta",
        "tenantb");

    // when / then resolution fails fast at boot on the shared snapshot repository
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("snapshot repository");
  }

  @Test
  void shouldResolveWhenSharedClusterButDistinctSnapshotRepositories() {
    // given two tenants on the same cluster that each override the inherited root repository-name
    setProperties(
        Map.of(
            "camunda.data.secondary-storage.elasticsearch.backup.repository-name", "camunda-backup",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.backup.repository-name",
                "camunda-backup-tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.backup.repository-name",
                "camunda-backup-tenantb"),
        "tenanta",
        "tenantb");

    // when / then distinct repositories isolate the tenants — resolution succeeds
    final PhysicalTenantResolver resolver = newResolver();
    assertThat(resolver.getAll())
        .containsOnlyKeys("tenanta", "tenantb", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  // --- exporter assignment wiring (ADR-0008 D1/D2/D5) --------------------------------------------
  // PhysicalTenantExporterAssignedValidation, PhysicalTenantExporterConfigurations#narrowToAssigned
  // and GenericExporterIsolationValidation are unit-tested standalone elsewhere; these tests
  // instead
  // pin that PhysicalTenantResolver#of wires them in, in the right order (validate against the
  // pre-narrow universe, narrow, then check isolation on what survived) — the three lines an
  // unrelated refactor could most easily drop or reorder.

  @Test
  void shouldRejectPhysicalTenantMissingExportersAssignedWhenGenericExporterExists() {
    // given a root-declared generic exporter and a tenant that never declares
    // camunda.physical-tenants.tenanta.data.exporters-assigned
    setProperties(
        Map.of(
            "camunda.data.exporters.custom.class-name", "com.acme.CustomExporter",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when / then resolution fails fast at boot — proves PhysicalTenantExporterAssignedValidation
    // is actually invoked from the resolver, not just independently unit-testable
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("tenanta")
        .withMessageContaining("exporters-assigned");
  }

  @Test
  void shouldNarrowAwayUnassignedGenericExporterThroughResolver() {
    // given two root-declared generic exporters; tenantA's manifest assigns only one of them
    setProperties(
        Map.of(
            "camunda.data.exporters.kept.class-name", "com.acme.KeptExporter",
            "camunda.data.exporters.dropped.class-name", "com.acme.DroppedExporter",
            "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "kept",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when — proves narrowToAssigned is actually invoked from the resolver on its resolved output
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then only the assigned entry survives in the resolved catalog
    assertThat(tenantA.getData().getExporters()).containsOnlyKeys("kept");
  }

  @Test
  void shouldKeepAutoconfiguredExporterAfterNarrowingThroughResolverEvenWhenUnassigned() {
    // given an already-materialized autoconfigured entry (simulating what secondary-storage
    // autoconfiguration would have produced) alongside a generic exporter the tenant does assign;
    // the autoconfigured id must never be listed in exporters-assigned (it is exempt/rejected)
    setProperties(
        Map.of(
            "camunda.data.exporters.camundaexporter.args.a", 1,
            "camunda.data.exporters.custom.class-name", "com.acme.CustomExporter",
            "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "custom",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then narrowing keeps both the assigned generic entry and the unassigned autoconfigured one
    assertThat(tenantA.getData().getExporters()).containsOnlyKeys("custom", "camundaexporter");
  }

  @Test
  void shouldRejectPhysicalTenantsWhoseAssignedGenericExportersShareATarget() {
    // given a root-declared generic exporter both tenants assign, each overriding its target to the
    // same value — the tenants' records would land in one place
    setProperties(
        Map.of(
            "camunda.data.exporters.claiming.class-name", TestExporterConfigMergers.CLAIMING_CLASS,
            "camunda.data.exporters.claiming.args.target", "root-target",
            "camunda.physical-tenants.tenanta.data.exporters.claiming.args.target", "shared-target",
            "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "claiming",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.exporters.claiming.args.target", "shared-target",
            "camunda.physical-tenants.tenantb.data.exporters-assigned[0]", "claiming",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"),
        "tenanta",
        "tenantb");

    // when / then resolution fails fast at boot — proves GenericExporterIsolationValidation is
    // actually invoked from the resolver, not just independently unit-testable
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("shared-target");
  }

  @Test
  void shouldNotFlagGenericExporterTargetNarrowedAwayFromATenant() {
    // given a root-declared generic exporter: tenantA assigns it under its own target, tenantB
    // declares an explicit empty manifest and so runs none
    setProperties(
        Map.of(
            "camunda.data.exporters.claiming.class-name", TestExporterConfigMergers.CLAIMING_CLASS,
            "camunda.data.exporters.claiming.args.target", "root-target",
            "camunda.physical-tenants.tenanta.data.exporters.claiming.args.target",
                "tenanta-target",
            "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "claiming",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.exporters-assigned", "",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"),
        "tenanta",
        "tenantb");

    // when — resolution succeeds
    final PhysicalTenantResolver resolver = newResolver();

    // then the isolation rule saw tenantB without the root entry: had it run before narrowing,
    // tenantB would still have carried the inherited entry on 'root-target' and collided with the
    // synthesized default tenant, which runs the root catalog as declared
    assertThat(resolver.forPhysicalTenant("tenantb").getData().getExporters())
        .doesNotContainKey("claiming");
    assertThat(resolver.forPhysicalTenant("tenanta").getData().getExporters())
        .containsKey("claiming");
  }

  @Test
  void shouldRejectInvalidTenantIds() {
    // tenant ids must be lowercase alphanumeric — no underscores, no uppercase, no dashes
    // (dashes would make yaml and env-var forms address two different tenants).
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId("Tenant_A"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId("-leading-dash"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId("tenant-a"))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectTenantIdExceeding64Characters() {
    // given a tenant id that is exactly one character over the limit
    final String tooLong = "a".repeat(PhysicalTenantResolver.MAX_TENANT_ID_LENGTH + 1);

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId(tooLong))
        .withMessageContaining("Invalid physical tenant id")
        .withMessageContaining("must not exceed " + PhysicalTenantResolver.MAX_TENANT_ID_LENGTH);
  }

  @Test
  void shouldAcceptTenantIdOfExactly64Characters() {
    // given a tenant id at exactly the maximum allowed length — must not throw
    final String maxLength = "a".repeat(PhysicalTenantResolver.MAX_TENANT_ID_LENGTH);

    // when / then no exception
    PhysicalTenantResolver.validateTenantId(maxLength);
  }
}
