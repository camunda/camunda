/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.UnixOperatingSystemMXBean;
import io.camunda.application.commons.secrets.InternalConfigRefreshers;
import io.camunda.client.CamundaClient;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.management.ObjectName;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@ZeebeIntegration
final class PhysicalTenantRestartLeakIT {

  private static final int WARM_UP_RESTARTS = 3;
  private static final int MEASURED_RESTARTS = 20;
  private static final Set<String> ROCKSDB_CLASSES =
      Set.of(
          "org.rocksdb.RocksDB",
          "org.rocksdb.OptimisticTransactionDB",
          "org.rocksdb.TransactionDB");
  private static final String ACTOR_TASK_CLASS = "io.camunda.zeebe.scheduler.ActorTask";
  // the forced garbage collections of the measurement register GC meters of their own
  private static final String JVM_METER_PREFIX = "jvm.";
  // gRPC event loops start their fixed number of threads lazily, so they are not a leak
  private static final String GRPC_EVENT_LOOP_THREAD = "grpc-nio-worker-";
  private static final String SECRET_CACHE_METER_PREFIX = "camunda.secret.cache.";
  // owned by the cluster configuration, which keeps a removed tenant's partition group, by the
  // gateway job stream client, which keeps one set of meters per tenant it has seen, or by the
  // shared messaging service, which keeps one set per topic
  private static final Set<String> METERS_KEPT_AFTER_REMOVE_PREFIXES =
      Set.of(
          "zeebe.cluster.partition.balanced",
          "zeebe.gateway.topology.",
          "zeebe.gateway.job.stream.",
          "zeebe.messaging.");

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @TempDir private Path secrets;
  private CamundaClient client;

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldNotLeakWhenRestartingOneTenantRepeatedly() throws IOException {
    // given
    final var tenantId =
        "leak" + Long.toString(Math.abs(UUID.randomUUID().getLeastSignificantBits()), 36);
    final var configuration = tenantConfiguration();
    lifecycle().add(tenantId, configuration);
    assertThat(secretCacheMeters(tenantId))
        .describedAs("the tenant's own secret stores are measured")
        .isNotEmpty();
    client = TENANTS.newClientBuilder(broker, tenantId).preferRestOverGrpc(false).build();
    awaitServing(tenantId);
    for (int i = 0; i < WARM_UP_RESTARTS; i++) {
      restart(tenantId, configuration);
    }
    final var before = Resources.measure(broker.bean(MeterRegistry.class), tenantId);

    // when
    final List<Long> restartMillis = new ArrayList<>();
    for (int i = 0; i < MEASURED_RESTARTS; i++) {
      restartMillis.add(restart(tenantId, configuration));
    }
    final var after = Resources.measure(broker.bean(MeterRegistry.class), tenantId);
    final var secretCacheMetersAfterRestarts = secretCacheMeters(tenantId);

    // then
    lifecycle().remove(tenantId);
    final var metersKeptAfterRemove =
        broker.bean(MeterRegistry.class).getMeters().stream()
            .filter(meter -> Resources.mentions(meter, tenantId))
            .filter(
                meter ->
                    METERS_KEPT_AFTER_REMOVE_PREFIXES.stream()
                        .noneMatch(meter.getId().getName()::startsWith))
            .map(meter -> meter.getId().toString())
            .toList();
    System.out.printf(
        "Tenant restart leak check over %d restarts: before=%s after=%s restartMillis=%s%n",
        MEASURED_RESTARTS, before.summary(), after.summary(), restartMillis);
    assertThat(after.metersOfOtherTenants())
        .containsExactlyInAnyOrderElementsOf(before.metersOfOtherTenants());
    assertThat(secretCacheMetersAfterRestarts)
        .describedAs("a restarted tenant keeps the meters of its secret stores")
        .isNotEmpty();
    assertThat(metersKeptAfterRemove).isEmpty();
    assertThat(secretCacheMeters(tenantId)).isEmpty();
    assertThat(refreshers().forPhysicalTenant(tenantId)).isEmpty();
    assertThat(after.rocksDbInstances()).isEqualTo(before.rocksDbInstances());
    assertThat(after.actorTasks()).isEqualTo(before.actorTasks());
    assertThat(after.threads()).isLessThanOrEqualTo(before.threads());
    assertThat(after.openFileDescriptors()).isLessThanOrEqualTo(before.openFileDescriptors() + 16);
  }

  private long restart(final String tenantId, final Map<String, Object> configuration) {
    final long start = System.nanoTime();
    lifecycle().replace(tenantId, configuration);
    awaitServing(tenantId);
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  private void awaitServing(final String tenantId) {
    Awaitility.await("physical tenant '%s' serves".formatted(tenantId))
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(10))
        .until(
            () ->
                registry().health(tenantId).map(PhysicalTenantHealth::state).orElse(null)
                    == State.HEALTHY);
    client
        .newPublishMessageCommand()
        .messageName("leak")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join(10, TimeUnit.SECONDS);
  }

  private List<String> secretCacheMeters(final String tenantId) {
    return broker.bean(MeterRegistry.class).getMeters().stream()
        .filter(meter -> meter.getId().getName().startsWith(SECRET_CACHE_METER_PREFIX))
        .filter(meter -> Resources.isTaggedWith(meter, tenantId))
        .map(meter -> meter.getId().toString())
        .toList();
  }

  private Map<String, Object> tenantConfiguration() throws IOException {
    final Map<String, Object> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put(
        "secrets.stores.file.default.path",
        Files.createDirectories(secrets.resolve("jobs")).toString());
    configuration.put(
        "secrets.stores.file.default.internal-config.path",
        Files.createDirectories(secrets.resolve("internal")).toString());
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private PhysicalTenantLifecycleService lifecycle() {
    return broker.bean(PhysicalTenantLifecycleService.class);
  }

  private InternalConfigRefreshers refreshers() {
    return broker.bean(InternalConfigRefreshers.class);
  }

  private DefaultPhysicalTenantRegistry registry() {
    return broker.bean(DefaultPhysicalTenantRegistry.class);
  }

  private record Resources(
      Set<String> metersOfOtherTenants,
      int tenantMeters,
      long rocksDbInstances,
      long actorTasks,
      int threads,
      long openFileDescriptors) {

    private static Resources measure(final MeterRegistry meterRegistry, final String tenantId) {
      final var histogram = liveInstancesAfterFullGc();
      final var meters = meterRegistry.getMeters();
      return new Resources(
          meters.stream()
              .filter(meter -> !isTaggedWith(meter, PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
              .filter(meter -> !mentions(meter, tenantId))
              .filter(meter -> !meter.getId().getName().startsWith(JVM_METER_PREFIX))
              .map(meter -> meter.getId().toString())
              .collect(Collectors.toSet()),
          (int) meters.stream().filter(meter -> isTaggedWith(meter, tenantId)).count(),
          ROCKSDB_CLASSES.stream().mapToLong(name -> histogram.getOrDefault(name, 0L)).sum(),
          histogram.getOrDefault(ACTOR_TASK_CLASS, 0L),
          (int)
              Thread.getAllStackTraces().keySet().stream()
                  .filter(thread -> !thread.getName().startsWith(GRPC_EVENT_LOOP_THREAD))
                  .count(),
          ManagementFactory.getOperatingSystemMXBean()
                  instanceof final UnixOperatingSystemMXBean unix
              ? unix.getOpenFileDescriptorCount()
              : -1);
    }

    private String summary() {
      return "meters(other tenants)=%d, meters(tenant)=%d, rocksDb=%d, actors=%d, threads=%d, fds=%d"
          .formatted(
              metersOfOtherTenants.size(),
              tenantMeters,
              rocksDbInstances,
              actorTasks,
              threads,
              openFileDescriptors);
    }

    private static boolean mentions(final Meter meter, final String tenantId) {
      return meter.getId().getTags().stream().anyMatch(tag -> tag.getValue().contains(tenantId));
    }

    private static boolean isTaggedWith(final Meter meter, final String tenantId) {
      return meter.getId().getTags().stream().anyMatch(tag -> tag.getValue().equals(tenantId));
    }

    private static Map<String, Long> liveInstancesAfterFullGc() {
      try {
        final String table =
            (String)
                ManagementFactory.getPlatformMBeanServer()
                    .invoke(
                        new ObjectName("com.sun.management:type=DiagnosticCommand"),
                        "gcClassHistogram",
                        new Object[] {new String[0]},
                        new String[] {String[].class.getName()});
        final Map<String, Long> instances = new HashMap<>();
        for (final String line : table.split("\n")) {
          final var columns = line.trim().split("\\s+");
          if (columns.length >= 4 && columns[0].endsWith(":")) {
            instances.merge(columns[3], Long.parseLong(columns[1]), Long::sum);
          }
        }
        return instances;
      } catch (final Exception e) {
        throw new IllegalStateException("Failed to read the class histogram", e);
      }
    }
  }
}
