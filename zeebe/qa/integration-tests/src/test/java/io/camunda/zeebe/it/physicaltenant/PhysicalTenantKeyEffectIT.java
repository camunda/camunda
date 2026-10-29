/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.zeebe.broker.PhysicalTenantConfigService;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import io.camunda.zeebe.it.physicaltenant.EffectiveState.Attributed;
import io.camunda.zeebe.it.physicaltenant.EffectiveState.Hit;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.unit.DataSize;

final class PhysicalTenantKeyEffectIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final Path REPORTS = Path.of("target", "physical-tenant-key-effect");
  private static final String ASSIGNED_EXPORTER = "harness.assigned-exporter";

  /** State that differs between two runs of the same configuration. */
  private static final Predicate<String> RUNTIME_NOISE =
      consumer ->
          consumer.equals(
              "io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy.initialOffset");

  /** State that differs between two runs or restarts of the same configuration. */
  private static final Predicate<String> RESTART_ARTEFACTS =
      consumer ->
          RUNTIME_NOISE.test(consumer)
              || consumer.startsWith("io.atomix.utils.serializer.")
              || consumer.startsWith("org.apache.commons.lang3.tuple.")
              || consumer.startsWith("io.micrometer.")
              || consumer.startsWith("io.camunda.zeebe.util.health.HealthIssue.")
              || consumer.startsWith(
                  "io.camunda.zeebe.transport.impl.AtomixServerTransport$$Lambda");

  /**
   * Keys whose value no consumer keeps as is: it is handed to RocksDB, written to the log, combined
   * with other values, or used by a component that this configuration does not start.
   */
  private static final Set<String> NOT_HELD_ON_THE_HEAP =
      Set.of(
          "processing.flow-control.request.legacy-vegas.alpha-limit",
          "processing.flow-control.request.legacy-vegas.beta-limit",
          "secrets.refresh-interval",
          "data.primary-storage.rocks-db.column-family-options.max_bytes_for_level_base",
          "cluster.global-listeners.user-task[0].type");

  /** Consumers that know only the tenants the node started with. */
  private static final Set<String> FROZEN_FOR_ADDED_TENANTS =
      Set.of(
          "io.camunda.secretstore.SecretCacheMetrics.storeId",
          "io.camunda.service.ClusterRuntimeBackupServices$PhysicalTenantBackupPort.backupIdGenerated",
          "io.camunda.service.ClusterRuntimeBackupServices$PhysicalTenantBackupPort.physicalTenantId",
          "io.camunda.zeebe.backup.common.CheckpointIdGenerator.offset",
          "io.camunda.zeebe.shared.management.BackupEndpoint$PhysicalTenantBackup.generatesIds");

  /**
   * The journal keeps the size of its current segment until it starts the next one, and the disk
   * usage of a tenant follows the size of the segments it preallocated.
   */
  private static final Map<String, Set<String>> APPLIED_ON_NEXT_SEGMENT =
      Map.of(
          "data.primary-storage.log-stream.log-segment-size",
          Set.of(
              "io.camunda.zeebe.journal.file.SegmentDescriptor.maxSegmentSize",
              "org.agrona.concurrent.UnsafeBuffer.capacity",
              "io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskSpaceUsageMonitor.usedBytes",
              "io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskSpaceUsageMonitor$$Lambda.arg$1",
              "io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskUsageMetrics$$Lambda.arg$1"));

  static Stream<SentinelBatch> sentinelBatches() {
    return Stream.of(
        new SentinelBatch("engine", Map.of(), engineSentinels()),
        new SentinelBatch(
            "exporter-arguments",
            Map.of(
                "data.exporters.keyeffect.class-name",
                ArgumentsExporter.class.getName(),
                ASSIGNED_EXPORTER,
                "keyeffect"),
            List.of(Sentinel.text("data.exporters.keyeffect.args.marker", "exporter-argument"))),
        new SentinelBatch(
            "flow-control-aimd",
            Map.of(
                "processing.flow-control.request.enabled", "true",
                "processing.flow-control.request.algorithm", "aimd"),
            List.of(
                Sentinel.integer("processing.flow-control.request.aimd.min-limit", 5_100_000),
                Sentinel.integer("processing.flow-control.request.aimd.initial-limit", 6_100_000),
                Sentinel.integer("processing.flow-control.request.aimd.max-limit", 8_100_000),
                Sentinel.duration(
                    "processing.flow-control.request.aimd.request-timeout", 7_100_000),
                Sentinel.decimal("processing.flow-control.request.aimd.backoff-ratio", 0.9101))),
        new SentinelBatch(
            "flow-control-fixed",
            Map.of(
                "processing.flow-control.request.enabled", "true",
                "processing.flow-control.request.algorithm", "fixed"),
            List.of(Sentinel.integer("processing.flow-control.request.fixed.limit", 6_200_000))),
        new SentinelBatch(
            "flow-control-gradient",
            Map.of(
                "processing.flow-control.request.enabled", "true",
                "processing.flow-control.request.algorithm", "gradient"),
            List.of(
                Sentinel.integer("processing.flow-control.request.gradient.min-limit", 5_300_000),
                Sentinel.integer(
                    "processing.flow-control.request.gradient.initial-limit", 6_300_000),
                Sentinel.decimal(
                    "processing.flow-control.request.gradient.rtt-tolerance", 2.3101))),
        new SentinelBatch(
            "flow-control-gradient2",
            Map.of(
                "processing.flow-control.request.enabled", "true",
                "processing.flow-control.request.algorithm", "gradient2"),
            List.of(
                Sentinel.integer("processing.flow-control.request.gradient2.min-limit", 5_400_000),
                Sentinel.integer(
                    "processing.flow-control.request.gradient2.initial-limit", 6_400_000),
                Sentinel.integer(
                    "processing.flow-control.request.gradient2.long-window", 7_400_000),
                Sentinel.decimal(
                    "processing.flow-control.request.gradient2.rtt-tolerance", 2.4101))),
        new SentinelBatch(
            "flow-control-vegas",
            Map.of(
                "processing.flow-control.request.enabled", "true",
                "processing.flow-control.request.algorithm", "vegas"),
            List.of(
                Sentinel.integer("processing.flow-control.request.vegas.alpha", 3_500_000),
                Sentinel.integer("processing.flow-control.request.vegas.beta", 4_500_000),
                Sentinel.integer(
                    "processing.flow-control.request.vegas.initial-limit", 6_500_000))),
        new SentinelBatch(
            "flow-control-legacy-vegas",
            Map.of(
                "processing.flow-control.request.enabled", "true",
                "processing.flow-control.request.algorithm", "legacy-vegas"),
            List.of(
                Sentinel.decimal(
                    "processing.flow-control.request.legacy-vegas.alpha-limit", 0.6101),
                Sentinel.decimal("processing.flow-control.request.legacy-vegas.beta-limit", 0.8101),
                Sentinel.integer(
                    "processing.flow-control.request.legacy-vegas.initial-limit", 6_600_000),
                Sentinel.integer(
                    "processing.flow-control.request.legacy-vegas.max-concurrency", 7_600_000))));
  }

  static Stream<SentinelBatch> rejectedBatches() {
    return Stream.of(
        new SentinelBatch(
            "rejected-outside-module",
            Map.of(),
            List.of(
                Sentinel.integer("api.rest.process-cache.max-size", 7_710_000),
                Sentinel.duration("api.rest.process-cache.expiration-idle", 7_720_000),
                Sentinel.integer("api.rest.cluster-variable.max-metadata-size", 7_730_000),
                Sentinel.integer("document.thread-pool-size", 7_740_000),
                Sentinel.integer("secrets.cache.max-size", 7_750_000),
                Sentinel.integer("secrets.max-concurrency", 7_770_000),
                Sentinel.duration("data.history-deletion.delay-between-runs", 7_780_000),
                Sentinel.duration("data.history-deletion.max-delay-between-runs", 7_790_000),
                Sentinel.integer("data.history-deletion.queue-batch-size", 7_800_000),
                Sentinel.duration("data.primary-storage.backup.checkpoint-interval", 7_810_000),
                Sentinel.text(
                    "data.extension-properties.tool-name-property", "tool-name-property"))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedBatches")
  void shouldShowWhichRejectedKeysAModuleRestartAppliesAnyway(final SentinelBatch batch)
      throws Exception {
    // when
    final var findings = runSentinelBatch(batch);

    // then
    assertThat(findings.get("data.primary-storage.backup.checkpoint-interval").stale())
        .contains("io.camunda.zeebe.backup.schedule.Schedule$IntervalSchedule.interval");
    assertThat(findings.get("data.primary-storage.backup.checkpoint-interval").notAdded())
        .contains("io.camunda.zeebe.backup.schedule.Schedule$IntervalSchedule.interval");
    assertThat(findings.get("document.thread-pool-size").replaced())
        .contains("io.camunda.document.api.DocumentStoreConfiguration.threadPoolSize");
    assertThat(findings.get("document.thread-pool-size").added())
        .contains("io.camunda.document.api.DocumentStoreConfiguration.threadPoolSize");
  }

  private static List<Sentinel> engineSentinels() {
    final List<Sentinel> sentinels = new ArrayList<>();
    int i = 0;
    for (final var key :
        List.of(
            "data.export.migration-status-scan-max-records",
            "monitoring.metrics.job-metrics.max-job-type-length",
            "monitoring.metrics.job-metrics.max-tenant-id-length",
            "monitoring.metrics.job-metrics.max-unique-keys",
            "monitoring.metrics.job-metrics.max-worker-name-length",
            "process-instance-creation.message-start-dedup-expiration-sweep-batch-limit",
            "process-instance-creation.message-start-lock-release-poll-batch-limit",
            "processing.engine.batch-operations.chunk-size",
            "processing.engine.batch-operations.query-in-clause-size",
            "processing.engine.batch-operations.query-page-size",
            "processing.engine.batch-operations.query-retry-max",
            "processing.engine.caches.authorizations-cache-capacity",
            "processing.engine.caches.drg-cache-capacity",
            "processing.engine.caches.form-cache-capacity",
            "processing.engine.caches.group-name-cache-capacity",
            "processing.engine.caches.process-cache-capacity",
            "processing.engine.caches.resource-cache-capacity",
            "processing.engine.job.timeout-checker-batch-limit",
            "processing.engine.max-process-depth",
            "processing.engine.messages.ttl-checker-batch-limit",
            "processing.engine.secrets.batch-resolution-limit",
            "processing.engine.secrets.retry-max-attempts",
            "processing.engine.validators.results-output-max-size",
            "processing.max-commands-in-batch",
            "processing.max-pending-side-effects",
            "processing.max-recoverable-retries",
            "data.primary-storage.log-stream.log-index-density",
            "data.primary-storage.rocks-db.max-open-files",
            "data.primary-storage.rocks-db.io-rate-bytes-per-second",
            "processing.flow-control.write.limit",
            "processing.flow-control.write.throttle.acceptable-backlog",
            "processing.flow-control.write.throttle.minimum-limit",
            "processing.skip-positions",
            "data.export.skip-records.1")) {
      sentinels.add(Sentinel.integer(key, 7_310_000 + 1000 * i++));
    }
    for (final var key :
        List.of(
            "data.export.distribution-interval",
            "data.snapshot-period",
            "expression.timeout",
            "monitoring.metrics.job-metrics.export-interval",
            "monitoring.metrics.usage-metrics.export-interval",
            "process-instance-creation.message-start-ask-retry-interval",
            "process-instance-creation.message-start-dedup-expiration-sweep-interval",
            "process-instance-creation.message-start-lock-release-poll-interval",
            "processing.engine.batch-operations.query-retry-initial-delay",
            "processing.engine.batch-operations.query-retry-max-delay",
            "processing.engine.batch-operations.scheduler-interval",
            "processing.engine.caches.authorizations-cache-ttl",
            "processing.engine.distribution.max-backoff-duration",
            "processing.engine.distribution.redistribution-interval",
            "processing.engine.job.timeout-checker-polling-interval",
            "processing.engine.messages.ttl-checker-interval",
            "processing.engine.secrets.interval",
            "processing.engine.secrets.retry-initial-delay",
            "processing.engine.secrets.retry-max-delay",
            "processing.engine.secrets.wake-delay",
            "processing.flow-control.write.ramp-up",
            "processing.flow-control.write.throttle.resolution",
            "processing.scheduled-tasks-check-interval",
            "secrets.refresh-interval")) {
      sentinels.add(Sentinel.duration(key, 7_310_000 + 1000 * i++));
    }
    sentinels.add(
        Sentinel.dataSize("data.primary-storage.disk.used-space.processing", 60_000_000_000L));
    sentinels.add(
        Sentinel.dataSize("data.primary-storage.disk.used-space.replication", 61_000_000_000L));
    sentinels.add(
        Sentinel.dataSize("data.primary-storage.log-stream.log-segment-size", 67_200_000L));
    sentinels.add(
        Sentinel.digits(
            "data.primary-storage.rocks-db.column-family-options.max_bytes_for_level_base",
            268_400_000));
    sentinels.add(Sentinel.text("cluster.global-listeners.user-task[0].type", "listener"));
    sentinels.add(Sentinel.text("data.primary-storage.runtime-directory", "/tmp/key-effect"));
    return sentinels;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sentinelBatches")
  void shouldApplyEveryRestartKeyToTheComponentsOfTheTenant(final SentinelBatch batch)
      throws Exception {
    // when
    final var findings = runSentinelBatch(batch);

    // then
    findings.forEach(
        (key, finding) -> {
          if (!NOT_HELD_ON_THE_HEAP.contains(key)) {
            assertThat(finding.booted()).as("consumers of %s", key).isNotEmpty();
          }
          final var allowed = APPLIED_ON_NEXT_SEGMENT.getOrDefault(key, Set.of());
          assertThat(without(finding.stale(), allowed))
              .as("consumers that keep the old value of %s after a replace", key)
              .isEmpty();
          assertThat(without(finding.notReplaced(), allowed))
              .as("consumers that do not get the new value of %s after a replace", key)
              .isEmpty();
          assertThat(without(finding.notAdded(), allowed))
              .as("consumers that do not get the value of %s for a tenant added at runtime", key)
              .isEmpty();
        });
  }

  private Map<String, Finding> runSentinelBatch(final SentinelBatch batch) throws Exception {
    final var a = uniqueTenantId("ka");
    final var b = uniqueTenantId("kb");
    final var c = uniqueTenantId("kc");
    final var common = new LinkedHashMap<>(batch.common());
    common.put("cluster.global-listeners.user-task[0].event-types[0]", "all");
    final Function<Role, Map<String, String>> configuration =
        role -> {
          final Map<String, String> properties = new LinkedHashMap<>(common);
          batch
              .sentinels()
              .forEach(sentinel -> properties.put(sentinel.key(), sentinel.value(role)));
          return properties;
        };
    try (final var app =
        App.boot(Map.of(a, configuration.apply(Role.X), b, configuration.apply(Role.Y)))) {
      app.lifecycle().replace(b, app.tenantConfiguration(b, configuration.apply(Role.X_REPLACED)));
      app.lifecycle().add(c, app.tenantConfiguration(c, configuration.apply(Role.X_ADDED)));
      app.awaitHealthy(a, b, c);

      final Map<String, String> tokens = new HashMap<>();
      final Map<String, String> strings = new HashMap<>();
      for (final var sentinel : batch.sentinels()) {
        for (final var role : Role.values()) {
          final var marked = sentinel.key() + "|" + role;
          sentinel.tokens(role).forEach(token -> tokens.put(token, marked));
          sentinel.substrings(role).forEach(substring -> strings.put(substring, marked));
        }
      }
      final var restConfigs = app.broker().bean(PhysicalTenantRestConfigProvider.class);
      List.of(a, b, c).forEach(restConfigs::forPhysicalTenant);
      final long started = System.nanoTime();
      final var hits = EffectiveState.find(app.roots(), tokens, strings, object -> false);
      final long tookMs = (System.nanoTime() - started) / 1_000_000;
      return reportSentinels(batch, Set.of(a, b, c), hits, tookMs);
    }
  }

  private static Set<String> without(final Set<String> consumers, final Set<String> allowed) {
    final var remaining = new TreeSet<>(consumers);
    remaining.removeAll(allowed);
    return remaining;
  }

  @Test
  void shouldNotApplyRootOnlyKeysToAnyComponentOfTheTenant() throws Exception {
    // given
    final var overridden = uniqueTenantId("ro");
    final var inheriting = uniqueTenantId("ri");
    final var control = uniqueTenantId("rc");
    final var rootOnly =
        Map.of(
            "system.clock-controlled", "true",
            "monitoring.metrics.actor", "true",
            "monitoring.jfr", "true");

    // when
    try (final var app =
        App.boot(Map.of(overridden, rootOnly, inheriting, Map.of(), control, Map.of()))) {
      final var byTenant =
          EffectiveState.attributed(
              app.roots(),
              Set.of(overridden, inheriting, control),
              object -> false,
              PhysicalTenantKeyEffectIT::isBookkeeping);

      // then
      final var tenants = Set.of(overridden, inheriting, control);
      final var noise = differingConsumers(byTenant, inheriting, control, tenants);
      final var differing = differingConsumers(byTenant, overridden, inheriting, tenants);
      differing.removeAll(noise);
      assertThat(without(differing, RESTART_ARTEFACTS)).isEmpty();
    }
  }

  @Test
  void shouldShowTheAppliedConfigurationOnlyThroughTheLifecycle() throws Exception {
    // given
    final var fileTenant = uniqueTenantId("af");
    final var apiTenant = uniqueTenantId("aa");
    final var key = "data.snapshot-period";
    try (final var app =
        App.boot(
            Map.of(fileTenant, Map.of(key, "11m")),
            Map.of(
                "management.endpoint.env.access", "unrestricted",
                "management.endpoint.env.show-values", "always"))) {
      final var service = app.broker().bean(PhysicalTenantConfigService.class);

      // when
      app.lifecycle().replace(fileTenant, app.tenantConfiguration(fileTenant, Map.of(key, "13m")));
      service.create(apiTenant, asText(app.tenantConfiguration(apiTenant, Map.of(key, "11m"))));
      app.awaitHealthy(apiTenant);
      service.update(apiTenant, asText(app.tenantConfiguration(apiTenant, Map.of(key, "13m"))));
      Awaitility.await("the update rolled out")
          .atMost(TIMEOUT)
          .until(() -> service.get(apiTenant).map(status -> status.rolledOut()).orElse(false));
      app.awaitHealthy(fileTenant, apiTenant);

      // then
      assertThat(app.monitoring("/actuator/env/camunda.physical-tenants." + fileTenant + "." + key))
          .contains("\"value\":\"11m\"")
          .doesNotContain("13m");
      assertThat(app.monitoring("/actuator/env/camunda.physical-tenants." + apiTenant + "." + key))
          .startsWith("404");
      assertThat(app.monitoring("/actuator/configprops"))
          .doesNotContain(fileTenant)
          .doesNotContain(apiTenant);
      assertThat(app.monitoring("/actuator/physicalTenants/" + apiTenant))
          .contains("\"version\":2", "\"rolledOut\":true")
          .doesNotContain("13m");
      for (final var tenant : List.of(fileTenant, apiTenant)) {
        assertThat(
                app.lifecycle().configuration(tenant).orElseThrow().getData().getSnapshotPeriod())
            .isEqualTo(Duration.ofMinutes(13));
      }
    }
  }

  private static Map<String, String> asText(final Map<String, Object> configuration) {
    final Map<String, String> text = new TreeMap<>();
    configuration.forEach((key, value) -> text.put(key, String.valueOf(value)));
    return text;
  }

  @Test
  void shouldKeepThePartitionsOfATenantWhoseCountChanges() throws Exception {
    // given
    final var tenant = uniqueTenantId("pc");
    try (final var app = App.boot(Map.of(tenant, Map.of("cluster.partition-count", "1")))) {

      // when
      app.lifecycle()
          .replace(tenant, app.tenantConfiguration(tenant, Map.of("cluster.partition-count", "2")));
      app.awaitHealthy(tenant);

      // then
      assertThat(
              app.lifecycle().configuration(tenant).orElseThrow().getCluster().getPartitionCount())
          .isEqualTo(2);
      final var partitions =
          EffectiveState.find(
                  app.roots(),
                  Map.of(),
                  Map.of("Partition-" + tenant + "-", "partition"),
                  object -> false)
              .stream()
              .map(Hit::value)
              .filter(value -> value.startsWith("Partition-" + tenant + "-"))
              .collect(Collectors.toCollection(TreeSet::new));
      assertThat(partitions).containsExactly("Partition-" + tenant + "-1");
    }
  }

  private static Set<String> differingConsumers(
      final Map<String, Map<String, Attributed>> byTenant,
      final String first,
      final String second,
      final Set<String> tenants) {
    final var consumers = new TreeSet<String>();
    consumers.addAll(byTenant.get(first).keySet());
    consumers.addAll(byTenant.get(second).keySet());
    consumers.removeIf(PhysicalTenantKeyEffectIT::isConfigurationClass);
    consumers.removeIf(
        consumer ->
            Objects.equals(
                normalise(byTenant.get(first).get(consumer), tenants),
                normalise(byTenant.get(second).get(consumer), tenants)));
    return consumers;
  }

  @Test
  void shouldApplyEveryRestartFlagAndChoiceToTheComponentsOfTheTenant() throws Exception {
    final Map<String, String[]> keys = new LinkedHashMap<>();
    for (final var key :
        List.of(
            "data.primary-storage.rocks-db.sst-partitioning-enabled",
            "data.primary-storage.rocks-db.wal-disabled",
            "data.wait-states.enabled",
            "monitoring.metrics.job-metrics.enabled",
            "processing.enable-straightthrough-processing-loop-detector",
            "processing.enable-yielding-due-date-checker",
            "processing.evaluate-boundary-event-correlation-key-in-activity-scope",
            "processing.flow-control.request.enabled",
            "processing.flow-control.request.windowed")) {
      keys.put(key, new String[] {"false", "true"});
    }
    for (final var key :
        List.of(
            "data.primary-storage.rocks-db.statistics-enabled",
            "monitoring.metrics.actor",
            "monitoring.metrics.enable-exporter-execution-metrics",
            "process-instance-creation.business-id-uniqueness-enabled",
            "processing.enable-async-message-ttl-checker",
            "processing.enable-async-timer-duedate-checker",
            "processing.enable-foreign-key-checks",
            "processing.enable-message-body-on-expired",
            "processing.enable-preconditions-check",
            "processing.engine.caches.candidate-group-name-resolution",
            "processing.engine.distribution.pause-command-distribution",
            "processing.engine.job.include-variables-in-job-completed-event",
            "processing.engine.startup.process-definition-restore-enabled",
            "processing.engine.startup.rpa-reexport-migration-enabled",
            "processing.flow-control.write.enabled",
            "processing.flow-control.write.throttle.enabled",
            "system.clock-controlled")) {
      keys.put(key, new String[] {"true", "false"});
    }
    keys.put("data.primary-storage.rocks-db.access-metrics", new String[] {"fine", "none"});
    keys.put("processing.engine.mappings.input-mode", new String[] {"ordered", "combined"});
    keys.put("processing.engine.mappings.output-mode", new String[] {"ordered", "combined"});
    keys.put(
        "processing.engine.mappings.input-comparison-mode", new String[] {"ordered", "combined"});
    keys.put(
        "processing.engine.mappings.output-comparison-mode", new String[] {"ordered", "combined"});
    keys.put("processing.flow-control.request.algorithm", new String[] {"vegas", "aimd"});
    keys.put("data.primary-storage.rocks-db.max-write-buffer-number", new String[] {"9", "6"});
    keys.put(
        "data.primary-storage.rocks-db.min-write-buffer-number-to-merge", new String[] {"4", "3"});
    keys.put(
        "processing.engine.batch-operations.query-retry-backoff-factor", new String[] {"5", "2"});
    keys.put("processing.engine.secrets.retry-backoff-factor", new String[] {"5", "2"});
    keys.put("processing.engine.storage-ordinals.fixed-storage-ordinal", new String[] {"3", "0"});

    final Map<String, String> x = new LinkedHashMap<>();
    final Map<String, String> y = new LinkedHashMap<>();
    keys.forEach(
        (key, values) -> {
          x.put(key, values[0]);
          y.put(key, values[1]);
        });

    final var a = uniqueTenantId("fa");
    final var twin = uniqueTenantId("ft");
    final var b = uniqueTenantId("fb");
    final var restarted = uniqueTenantId("fr");
    final var c = uniqueTenantId("fc");
    try (final var app = App.boot(Map.of(a, x, twin, x, b, y, restarted, x))) {
      app.lifecycle().replace(b, app.tenantConfiguration(b, x));
      app.lifecycle().replace(restarted, app.tenantConfiguration(restarted, x));
      app.lifecycle().add(c, app.tenantConfiguration(c, x));
      app.awaitHealthy(a, twin, b, restarted, c);

      final var tenants = Set.of(a, twin, b, restarted, c);
      final var byTenant =
          EffectiveState.attributed(
              app.roots(), tenants, object -> false, PhysicalTenantKeyEffectIT::isBookkeeping);
      final var differences =
          reportAttributed(
              "flags-and-choices", byTenant, a, twin, b, restarted, c, keys.keySet().size());

      // then
      assertThat(without(differences.replaced(), RESTART_ARTEFACTS::test))
          .as("consumers of a replaced tenant that differ from a tenant restarted unchanged")
          .isEmpty();
      assertThat(without(differences.added(), RESTART_ARTEFACTS::test))
          .as("consumers of a tenant added at runtime that differ from a boot tenant")
          .isSubsetOf(FROZEN_FOR_ADDED_TENANTS);
    }
  }

  private static Set<String> without(final Set<String> consumers, final Predicate<String> ignored) {
    final var remaining = new TreeSet<>(consumers);
    remaining.removeIf(ignored);
    return remaining;
  }

  private static Map<String, Finding> reportSentinels(
      final SentinelBatch batch, final Set<String> tenants, final List<Hit> hits, final long tookMs)
      throws IOException {
    final var out = new StringBuilder();
    final var summary = new StringBuilder();
    summary
        .append("walk took ")
        .append(tookMs)
        .append(" ms, hits ")
        .append(hits.size())
        .append('\n');
    final Map<String, Map<Role, TreeMap<String, String>>> byKey = new TreeMap<>();
    for (final var hit : hits) {
      var path = hit.path();
      for (final var tenant : tenants) {
        path = path.replace(tenant, "<T>");
      }
      if (isBookkeeping(path) || isConfigurationClass(hit.consumer())) {
        continue;
      }
      final var keyAndRole = hit.marked().split("\\|");
      byKey
          .computeIfAbsent(keyAndRole[0], k -> new TreeMap<>())
          .computeIfAbsent(Role.valueOf(keyAndRole[1]), r -> new TreeMap<>())
          .putIfAbsent(hit.consumer(), path);
    }
    final Map<String, Finding> findings = new LinkedHashMap<>();
    for (final var sentinel : batch.sentinels()) {
      final var consumers = byKey.getOrDefault(sentinel.key(), Map.of());
      final var booted = consumers.getOrDefault(Role.X, new TreeMap<>());
      final var stale = consumers.getOrDefault(Role.Y, new TreeMap<>());
      final var replaced = consumers.getOrDefault(Role.X_REPLACED, new TreeMap<>());
      final var added = consumers.getOrDefault(Role.X_ADDED, new TreeMap<>());
      final var notReplaced = new TreeSet<>(booted.keySet());
      notReplaced.removeAll(replaced.keySet());
      final var notAdded = new TreeSet<>(booted.keySet());
      notAdded.removeAll(added.keySet());
      final var finding =
          new Finding(
              booted.keySet(),
              stale.keySet(),
              replaced.keySet(),
              added.keySet(),
              notReplaced,
              notAdded);
      findings.put(sentinel.key(), finding);
      summary
          .append(sentinel.key())
          .append(" | consumers ")
          .append(booted.keySet())
          .append(" | old value kept ")
          .append(stale.keySet())
          .append(" | missing after replace ")
          .append(notReplaced)
          .append(" | missing when added ")
          .append(notAdded)
          .append('\n');
      out.append("\n== ").append(sentinel.key()).append('\n');
      append(out, "booted    ", booted);
      append(out, "OLD VALUE ", stale);
      append(out, "replaced  ", replaced);
      append(out, "added     ", added);
    }
    Files.createDirectories(REPORTS);
    Files.writeString(REPORTS.resolve(batch.name() + ".txt"), summary.append('\n').append(out));
    return findings;
  }

  private static void append(
      final StringBuilder out, final String label, final Map<String, String> consumers) {
    consumers.forEach(
        (consumer, path) ->
            out.append("  ")
                .append(label)
                .append(consumer)
                .append("  @ ")
                .append(path)
                .append('\n'));
  }

  /**
   * Paths that hold configuration objects or the lifecycle's own records rather than a consumer:
   * the boot snapshots, the lifecycle's current configurations, the applied subtrees, the Spring
   * environment and the cluster configuration.
   */
  private static boolean isBookkeeping(final String path) {
    return isBootSnapshot(path)
        || path.startsWith("@physicalTenantLifecycleService.")
        || path.startsWith("@physicalTenantConfigExecutor.")
        || path.startsWith("@environment.")
        || path.startsWith("@brokerTopologyManagerForEmbeddedBrokerClient.")
        || path.startsWith("@broker.brokerContext.clusterConfigurationService.");
  }

  private static boolean isConfigurationClass(final String consumer) {
    return consumer.startsWith("io.camunda.configuration.")
        || consumer.startsWith("io.camunda.zeebe.broker.system.configuration.")
        || consumer.startsWith("io.camunda.zeebe.gateway.impl.configuration.")
        || consumer.startsWith("io.camunda.security.api.model.config.");
  }

  private static boolean isBootSnapshot(final String path) {
    return path.startsWith("@physicalTenantResolver.resolved[")
        || path.startsWith("@broker.systemContext.physicalTenantContexts[");
  }

  private static Differences reportAttributed(
      final String name,
      final Map<String, Map<String, Attributed>> byTenant,
      final String a,
      final String twin,
      final String b,
      final String restarted,
      final String c,
      final int keyCount)
      throws IOException {
    final var out = new StringBuilder();
    out.append("keys ").append(keyCount).append('\n');
    final var consumers = new TreeSet<String>();
    byTenant.values().forEach(map -> consumers.addAll(map.keySet()));
    final Function<String, Function<String, List<String>>> values =
        tenant ->
            consumer ->
                normalise(byTenant.get(tenant).get(consumer), Set.of(a, twin, b, restarted, c));
    int noise = 0;
    final var replacedDiffers = new TreeSet<String>();
    final var addedDiffers = new TreeSet<String>();
    for (final var consumer : consumers) {
      if (isConfigurationClass(consumer)) {
        continue;
      }
      final var ofA = values.apply(a).apply(consumer);
      final var ofTwin = values.apply(twin).apply(consumer);
      if (!Objects.equals(ofA, ofTwin)) {
        noise++;
        continue;
      }
      final var ofB = values.apply(b).apply(consumer);
      final var ofRestarted = values.apply(restarted).apply(consumer);
      final var ofC = values.apply(c).apply(consumer);
      if (!Objects.equals(ofB, ofRestarted)) {
        replacedDiffers.add(consumer);
        out.append("REPLACED DIFFERS ")
            .append(consumer)
            .append("\n    replaced: ")
            .append(ofB)
            .append("\n    restarted: ")
            .append(ofRestarted)
            .append("\n    @ ")
            .append(examplePath(byTenant, b, restarted, consumer))
            .append('\n');
      }
      if (!Objects.equals(ofC, ofA)) {
        addedDiffers.add(consumer);
        out.append("ADDED DIFFERS ")
            .append(consumer)
            .append("\n    added: ")
            .append(ofC)
            .append("\n    booted: ")
            .append(ofA)
            .append("\n    @ ")
            .append(examplePath(byTenant, c, a, consumer))
            .append('\n');
      }
    }
    out.append("consumers ").append(consumers.size()).append(", noise ").append(noise).append('\n');
    Files.createDirectories(REPORTS);
    Files.writeString(REPORTS.resolve(name + ".txt"), out);
    return new Differences(replacedDiffers, addedDiffers);
  }

  private static String examplePath(
      final Map<String, Map<String, Attributed>> byTenant,
      final String first,
      final String second,
      final String consumer) {
    return Stream.of(first, second)
        .map(tenant -> byTenant.get(tenant).get(consumer))
        .filter(Objects::nonNull)
        .map(Attributed::examplePath)
        .findFirst()
        .orElse("?");
  }

  private static List<String> normalise(final Attributed attributed, final Set<String> tenants) {
    if (attributed == null) {
      return null;
    }
    return attributed.values().stream()
        .map(
            value -> {
              var normalised = value;
              for (final var tenant : tenants) {
                normalised = normalised.replace(tenant, "<T>");
              }
              return normalised;
            })
        .sorted()
        .toList();
  }

  private static String uniqueTenantId(final String prefix) {
    return prefix + Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36);
  }

  record Differences(Set<String> replaced, Set<String> added) {}

  record Finding(
      Set<String> booted,
      Set<String> stale,
      Set<String> replaced,
      Set<String> added,
      Set<String> notReplaced,
      Set<String> notAdded) {}

  enum Role {
    /** booted with it, never changed */
    X,
    /** booted with it, then replaced */
    Y,
    /** the value it was replaced with */
    X_REPLACED,
    /** added at runtime with it */
    X_ADDED
  }

  record SentinelBatch(String name, Map<String, String> common, List<Sentinel> sentinels) {
    @Override
    public String toString() {
      return name;
    }
  }

  record Sentinel(
      String key, Map<Role, String> values, Map<Role, List<String>> tokens, boolean substring) {

    static Sentinel integer(final String key, final long base) {
      return of(
          key,
          role -> base + 11 + 23L * role.ordinal(),
          Object::toString,
          value -> List.of("n:" + value),
          false);
    }

    static Sentinel duration(final String key, final long baseMillis) {
      return of(
          key,
          role -> baseMillis + 11 + 23L * role.ordinal(),
          millis -> millis + "ms",
          millis -> EffectiveState.tokensOf(Duration.ofMillis(millis)),
          false);
    }

    static Sentinel dataSize(final String key, final long baseBytes) {
      return of(
          key,
          role -> baseBytes + 11 + 23L * role.ordinal(),
          bytes -> bytes + "B",
          bytes -> EffectiveState.tokensOf(DataSize.ofBytes(bytes)),
          false);
    }

    static Sentinel digits(final String key, final long base) {
      return of(
          key,
          role -> base + 11 + 23L * role.ordinal(),
          Object::toString,
          value -> List.of(value.toString()),
          true);
    }

    static Sentinel decimal(final String key, final double base) {
      final Map<Role, String> values = new LinkedHashMap<>();
      final Map<Role, List<String>> tokens = new LinkedHashMap<>();
      for (final var role : Role.values()) {
        final double value = Math.round((base + 0.0011 * role.ordinal()) * 10_000) / 10_000.0;
        values.put(role, Double.toString(value));
        tokens.put(role, List.of("d:" + value));
      }
      return new Sentinel(key, values, tokens, false);
    }

    static Sentinel text(final String key, final String base) {
      final Map<Role, String> values = new LinkedHashMap<>();
      final Map<Role, List<String>> tokens = new LinkedHashMap<>();
      for (final var role : Role.values()) {
        final var value = base + "-" + role.name().toLowerCase().replace('_', '-') + "-s7310";
        values.put(role, value);
        tokens.put(role, List.of(value));
      }
      return new Sentinel(key, values, tokens, true);
    }

    private static Sentinel of(
        final String key,
        final Function<Role, Long> raw,
        final Function<Long, String> property,
        final Function<Long, List<String>> token,
        final boolean substring) {
      final Map<Role, String> values = new LinkedHashMap<>();
      final Map<Role, List<String>> tokens = new LinkedHashMap<>();
      for (final var role : Role.values()) {
        final long value = raw.apply(role);
        values.put(role, property.apply(value));
        tokens.put(role, token.apply(value));
      }
      return new Sentinel(key, values, tokens, substring);
    }

    String value(final Role role) {
      return values.get(role);
    }

    List<String> tokens(final Role role) {
      return substring ? List.of() : tokens.get(role);
    }

    List<String> substrings(final Role role) {
      return substring ? tokens.get(role) : List.of();
    }
  }

  /** Keeps the arguments it was configured with, so the harness can find them. */
  public static final class ArgumentsExporter implements Exporter {

    private Map<String, Object> arguments = Map.of();

    @Override
    public void configure(final Context context) {
      arguments = Map.copyOf(context.getConfiguration().getArguments());
    }

    @Override
    public void export(final Record<?> record) {}
  }

  private record App(
      TestStandaloneBroker broker,
      ConfigurableApplicationContext context,
      boolean hasRecordingExporter)
      implements AutoCloseable {

    static App boot(final Map<String, Map<String, String>> bootTenants) {
      return boot(bootTenants, Map.of());
    }

    static App boot(
        final Map<String, Map<String, String>> bootTenants,
        final Map<String, String> nodeProperties) {
      final var context = new AtomicReference<ConfigurableApplicationContext>();
      final var broker =
          PhysicalTenantsITHelper.builder()
              .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
              .build()
              .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
              .withAdditionalInitializer(ctx -> context.set((ConfigurableApplicationContext) ctx));
      final var hasRecordingExporter =
          broker
              .unifiedConfig()
              .getData()
              .getExporters()
              .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID);
      final var app = new App(broker, null, hasRecordingExporter);
      bootTenants.forEach(
          (tenant, properties) ->
              app.tenantConfiguration(tenant, properties)
                  .forEach(
                      (key, value) ->
                          broker.withProperty(
                              "camunda.physical-tenants." + tenant + "." + key, value)));
      nodeProperties.forEach(broker::withProperty);
      broker.start();
      final var started = new App(broker, context.get(), hasRecordingExporter);
      started.awaitHealthy(bootTenants.keySet().toArray(String[]::new));
      return started;
    }

    Map<String, Object> tenantConfiguration(
        final String tenantId, final Map<String, String> properties) {
      final Map<String, Object> configuration = new LinkedHashMap<>();
      configuration.put("name", "Key effect " + tenantId);
      configuration.put("data.secondary-storage.type", "none");
      configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
      final List<String> assigned = new ArrayList<>();
      if (hasRecordingExporter) {
        assigned.add(TestStandaloneBroker.RECORDING_EXPORTER_ID);
      }
      final Map<String, String> remaining = new LinkedHashMap<>(properties);
      Optional.ofNullable(remaining.remove(ASSIGNED_EXPORTER)).ifPresent(assigned::add);
      for (int i = 0; i < assigned.size(); i++) {
        configuration.put("data.exporters-assigned[" + i + "]", assigned.get(i));
      }
      configuration.putAll(remaining);
      return new HashMap<>(configuration);
    }

    PhysicalTenantLifecycleService lifecycle() {
      return broker.bean(PhysicalTenantLifecycleService.class);
    }

    void awaitHealthy(final String... tenants) {
      final var registry = broker.bean(DefaultPhysicalTenantRegistry.class);
      for (final var tenant : tenants) {
        Awaitility.await("tenant " + tenant + " is healthy")
            .atMost(TIMEOUT)
            .until(
                () ->
                    registry.health(tenant).map(PhysicalTenantHealth::state).orElse(null)
                        == State.HEALTHY);
        try {
          lifecycle().awaitReady(tenant).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
          throw new IllegalStateException(e);
        }
      }
    }

    String monitoring(final String path) throws Exception {
      try (final var http = HttpClient.newHttpClient()) {
        final var response =
            http.send(
                HttpRequest.newBuilder(broker.monitoringUri(path)).build(),
                BodyHandlers.ofString());
        return response.statusCode() + " " + response.body();
      }
    }

    Map<String, Object> roots() {
      final Map<String, Object> roots = new LinkedHashMap<>();
      final var beanFactory = context.getBeanFactory();
      for (final var name : new LinkedHashSet<>(List.of(beanFactory.getSingletonNames()))) {
        roots.put("@" + name, beanFactory.getSingleton(name));
      }
      return roots;
    }

    @Override
    public void close() {
      broker.close();
    }
  }
}
