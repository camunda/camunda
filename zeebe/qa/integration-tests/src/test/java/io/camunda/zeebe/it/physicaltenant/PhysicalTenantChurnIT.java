/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.zeebe.broker.PhysicalTenantChangeException;
import io.camunda.zeebe.broker.PhysicalTenantChangeException.Reason;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a node keeps of tenants that were added and removed at runtime. */
@ZeebeIntegration
final class PhysicalTenantChurnIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final int TENANTS = 5;

  @TestZeebe
  private final TestStandaloneBroker broker =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build()
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @TempDir private Path backups;

  @Test
  void shouldKeepMetersOfTenantsRemovedAtRuntime() throws Exception {
    // given
    final List<String> removed = new ArrayList<>();
    for (int i = 0; i < TENANTS; i++) {
      final var tenantId = uniqueTenantId("ch");
      lifecycle().add(tenantId, tenantConfiguration(Map.of()));
      awaitHealthy(tenantId);
      removed.add(tenantId);
    }

    // when
    removed.forEach(lifecycle()::remove);

    // then
    final var meters = metersNaming(removed);
    report(meters);
    assertThat(meters.keySet()).containsExactlyInAnyOrderElementsOf(removed);
    assertThat(meters.values())
        .allSatisfy(
            names ->
                assertThat(names)
                    .contains("zeebe.cluster.partition.balanced{broker-id,partition}"));
    assertThat(meters.values().stream().flatMap(Set::stream))
        .contains(
            "zeebe.gateway.job.stream.clients{broker-id}",
            "zeebe.gateway.topology.partition.roles{broker,broker-id,partition}");
  }

  @Test
  void shouldLetATenantClaimTheBackupLocationOfARemovedTenant() throws Exception {
    // given
    final var location = Files.createDirectories(backups.resolve(uniqueTenantId("bk")));
    final var backup =
        Map.of(
            "data.primary-storage.backup.store",
            "filesystem",
            "data.primary-storage.backup.filesystem.base-path",
            location.toString());
    final var first = uniqueTenantId("bf");
    final var second = uniqueTenantId("bs");
    lifecycle().add(first, tenantConfiguration(backup));
    awaitHealthy(first);
    final var whileItRuns =
        catchThrowable(() -> lifecycle().validate(second, tenantConfiguration(backup)));

    // when
    lifecycle().remove(first);

    // then
    assertThat(whileItRuns)
        .isInstanceOfSatisfying(
            PhysicalTenantChangeException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID))
        .hasMessageContaining("share the same backup location");
    assertThat(lifecycle().validate(second, tenantConfiguration(backup))).isNotNull();
  }

  private Map<String, Set<String>> metersNaming(final List<String> tenants) {
    return broker.bean(MeterRegistry.class).getMeters().stream()
        .flatMap(
            meter ->
                meter.getId().getTags().stream()
                    .filter(tag -> tenants.contains(tag.getValue()))
                    .map(tag -> Map.entry(tag.getValue(), describe(meter))))
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(TreeSet::new))));
  }

  private static String describe(final Meter meter) {
    return meter.getId().getName()
        + meter.getId().getTags().stream()
            .filter(tag -> !tag.getKey().toLowerCase().contains("tenant"))
            .map(tag -> tag.getKey())
            .sorted()
            .collect(Collectors.joining(",", "{", "}"));
  }

  private static void report(final Map<String, Set<String>> meters) throws Exception {
    final var dir = Path.of("target", "physical-tenant-key-effect");
    Files.createDirectories(dir);
    final var out = new StringBuilder();
    meters.forEach(
        (tenant, names) -> {
          out.append(tenant).append(' ').append(names.size()).append('\n');
          names.forEach(name -> out.append("  ").append(name).append('\n'));
        });
    Files.writeString(dir.resolve("churn-meters.txt"), out);
  }

  private Map<String, Object> tenantConfiguration(final Map<String, String> extra) {
    final Map<String, Object> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    configuration.putAll(extra);
    return configuration;
  }

  private void awaitHealthy(final String tenantId) throws Exception {
    final var registry = broker.bean(DefaultPhysicalTenantRegistry.class);
    Awaitility.await("tenant " + tenantId + " is healthy")
        .atMost(TIMEOUT)
        .until(
            () ->
                registry.health(tenantId).map(PhysicalTenantHealth::state).orElse(null)
                    == State.HEALTHY);
    lifecycle().awaitReady(tenantId).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  private PhysicalTenantLifecycleService lifecycle() {
    return broker.bean(PhysicalTenantLifecycleService.class);
  }

  private static String uniqueTenantId(final String prefix) {
    return prefix + Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36);
  }
}
