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
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.it.physicaltenant.EffectiveState.Hit;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.unit.DataSize;

/**
 * Changes every {@code RESTART} key of the {@code default} tenant at runtime and compares what the
 * node's components hold with what a node restart with the new value gives them: one broker boots
 * {@code default} with value X; another boots it with Y and replaces it at runtime with X. A
 * consumer that holds X after the boot must hold the replaced value after the replace, and no
 * consumer may still hold Y. Ported from {@code PhysicalTenantKeyEffectIT} (branch {@code
 * pt-poc-tenant-config-consumers}), whose tenants are never {@code default}.
 *
 * <p>Beyond consumers that copy a value, it lists the beans that still hold a configuration object
 * with Y after the replace: a bean that keeps the boot {@code Camunda} or {@code BrokerCfg} of
 * {@code default} keeps its old values. Reports land in {@code
 * target/physical-tenant-key-effect/default-*.txt}.
 */
final class PhysicalTenantDefaultKeyEffectIT {

  private static final String DEFAULT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final Path REPORTS = Path.of("target", "physical-tenant-key-effect");
  private static final String ASSIGNED_EXPORTER = "harness.assigned-exporter";

  /** As in PhysicalTenantKeyEffectIT: values no consumer keeps as they are. */
  private static final Set<String> NOT_HELD_ON_THE_HEAP =
      Set.of(
          "processing.flow-control.request.legacy-vegas.alpha-limit",
          "processing.flow-control.request.legacy-vegas.beta-limit",
          "secrets.refresh-interval",
          "data.primary-storage.rocks-db.column-family-options.max_bytes_for_level_base",
          "cluster.global-listeners.user-task[0].type");

  /** As in PhysicalTenantKeyEffectIT: the open journal segment keeps its size. */
  private static final Map<String, Set<String>> APPLIED_ON_NEXT_SEGMENT =
      Map.of(
          "data.primary-storage.log-stream.log-segment-size",
          Set.of(
              "io.camunda.zeebe.journal.file.SegmentDescriptor.maxSegmentSize",
              "org.agrona.concurrent.UnsafeBuffer.capacity",
              "io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskSpaceUsageMonitor.usedBytes",
              "io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskSpaceUsageMonitor$$Lambda.arg$1",
              "io.camunda.zeebe.broker.system.monitoring.PhysicalTenantDiskUsageMetrics$$Lambda.arg$1"));

  /**
   * Consumers that keep the boot value of {@code default} after a replace, by key, found by this
   * harness; the notes explain each.
   */
  private static final Map<String, Set<String>> KNOWN_STALE = Map.of();

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
  void shouldApplyEveryRestartKeyOfTheDefaultTenantToItsComponents(final SentinelBatch batch)
      throws Exception {
    // when
    final var findings = runSentinelBatch(batch);

    // then
    findings.forEach(
        (key, finding) -> {
          if (!NOT_HELD_ON_THE_HEAP.contains(key)) {
            assertThat(finding.booted()).as("consumers of %s", key).isNotEmpty();
          }
          final var allowed = new TreeSet<>(APPLIED_ON_NEXT_SEGMENT.getOrDefault(key, Set.of()));
          allowed.addAll(KNOWN_STALE.getOrDefault(key, Set.of()));
          assertThat(without(finding.stale(), allowed))
              .as("consumers that keep the old value of %s after a replace of default", key)
              .isEmpty();
          assertThat(without(finding.notReplaced(), allowed))
              .as("consumers that do not get the new value of %s after a replace of default", key)
              .isEmpty();
        });
  }

  private Map<String, Finding> runSentinelBatch(final SentinelBatch batch) throws Exception {
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
    final Map<String, String> tokens = new HashMap<>();
    final Map<String, String> strings = new HashMap<>();
    for (final var sentinel : batch.sentinels()) {
      for (final var role : Role.values()) {
        final var marked = sentinel.key() + "|" + role;
        sentinel.tokens(role).forEach(token -> tokens.put(token, marked));
        sentinel.substrings(role).forEach(substring -> strings.put(substring, marked));
      }
    }

    final List<Hit> booted;
    try (final var app = App.boot(configuration.apply(Role.X))) {
      booted = EffectiveState.find(app.roots(), tokens, strings, object -> false);
    }
    final List<Hit> replaced;
    final long replaceMs;
    try (final var app = App.boot(configuration.apply(Role.Y))) {
      final long started = System.nanoTime();
      app.lifecycle()
          .replace(DEFAULT, app.tenantConfiguration(configuration.apply(Role.X_REPLACED)));
      app.awaitHealthy();
      replaceMs = (System.nanoTime() - started) / 1_000_000;
      replaced = EffectiveState.find(app.roots(), tokens, strings, object -> false);
    }
    return report(batch, booted, replaced, replaceMs);
  }

  private static Map<String, Finding> report(
      final SentinelBatch batch,
      final List<Hit> booted,
      final List<Hit> replaced,
      final long replaceMs)
      throws IOException {
    final Map<String, Map<Role, TreeMap<String, String>>> consumers = new TreeMap<>();
    final Map<String, TreeMap<String, String>> staleConfigurationHolders = new TreeMap<>();
    final var all = new ArrayList<Hit>(booted);
    all.addAll(replaced);
    for (final var hit : all) {
      final var keyAndRole = hit.marked().split("\\|");
      final var role = Role.valueOf(keyAndRole[1]);
      final var fromBoot = booted.contains(hit) && role == Role.X;
      final var fromReplace = replaced.contains(hit) && role != Role.X;
      if (!fromBoot && !fromReplace || isBookkeeping(hit.path())) {
        continue;
      }
      if (isConfigurationClass(hit.consumer())) {
        if (fromReplace && role == Role.Y) {
          staleConfigurationHolders
              .computeIfAbsent(keyAndRole[0], k -> new TreeMap<>())
              .putIfAbsent(rootBean(hit.path()), hit.path());
        }
        continue;
      }
      consumers
          .computeIfAbsent(keyAndRole[0], k -> new TreeMap<>())
          .computeIfAbsent(role, r -> new TreeMap<>())
          .putIfAbsent(hit.consumer(), hit.path());
    }
    final var summary = new StringBuilder();
    summary.append("replace of default took ").append(replaceMs).append(" ms\n");
    final var details = new StringBuilder();
    final Map<String, Finding> findings = new LinkedHashMap<>();
    for (final var sentinel : batch.sentinels()) {
      final var byRole = consumers.getOrDefault(sentinel.key(), Map.of());
      final var ofX = byRole.getOrDefault(Role.X, new TreeMap<>());
      final var ofY = byRole.getOrDefault(Role.Y, new TreeMap<>());
      final var ofReplaced = byRole.getOrDefault(Role.X_REPLACED, new TreeMap<>());
      final var notReplaced = new TreeSet<>(ofX.keySet());
      notReplaced.removeAll(ofReplaced.keySet());
      final var holders = staleConfigurationHolders.getOrDefault(sentinel.key(), new TreeMap<>());
      findings.put(
          sentinel.key(), new Finding(ofX.keySet(), ofY.keySet(), notReplaced, holders.keySet()));
      summary
          .append(sentinel.key())
          .append(" | consumers ")
          .append(ofX.keySet())
          .append(" | old value kept ")
          .append(ofY.keySet())
          .append(" | missing after replace ")
          .append(notReplaced)
          .append(" | old configuration object held by ")
          .append(holders.keySet())
          .append('\n');
      details.append("\n== ").append(sentinel.key()).append('\n');
      append(details, "booted    ", ofX);
      append(details, "OLD VALUE ", ofY);
      append(details, "replaced  ", ofReplaced);
      append(details, "OLD CONFIG", holders);
    }
    Files.createDirectories(REPORTS);
    Files.writeString(
        REPORTS.resolve("default-" + batch.name() + ".txt"), summary.append('\n').append(details));
    return findings;
  }

  private static void append(
      final StringBuilder out, final String label, final Map<String, String> consumers) {
    consumers.forEach(
        (consumer, path) ->
            out.append("  ")
                .append(label)
                .append(' ')
                .append(consumer)
                .append("  @ ")
                .append(path)
                .append('\n'));
  }

  private static String rootBean(final String path) {
    final var dot = path.indexOf('.');
    return dot < 0 ? path : path.substring(0, dot);
  }

  /**
   * Paths that hold configuration as records rather than as a consumer: the boot snapshots, the
   * lifecycle's and the file input's own records, the Spring environment and the cluster
   * configuration.
   */
  private static boolean isBookkeeping(final String path) {
    return path.startsWith("@physicalTenantResolver.resolved[")
        || path.startsWith("@broker.systemContext.physicalTenantContexts[")
        || path.startsWith("@physicalTenantLifecycleService.")
        || path.startsWith("@physicalTenantConfigExecutor.")
        || path.startsWith("@physicalTenantConfigService.")
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

  private static Set<String> without(final Set<String> consumers, final Set<String> allowed) {
    final var remaining = new TreeSet<>(consumers);
    remaining.removeAll(allowed);
    return remaining;
  }

  record Finding(
      Set<String> booted,
      Set<String> stale,
      Set<String> notReplaced,
      Set<String> staleConfigurationHolders) {}

  enum Role {
    /** booted with it, never changed */
    X,
    /** booted with it, then replaced */
    Y,
    /** the value it was replaced with */
    X_REPLACED
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

    static App boot(final Map<String, String> defaultProperties) {
      final var context = new AtomicReference<ConfigurableApplicationContext>();
      final var broker =
          PhysicalTenantsITHelper.builder()
              .withTenant(DEFAULT, Storage.none())
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
      app.tenantConfiguration(defaultProperties)
          .forEach(
              (key, value) ->
                  broker.withProperty("camunda.physical-tenants.default." + key, value));
      broker.start();
      final var started = new App(broker, context.get(), hasRecordingExporter);
      started.awaitHealthy();
      return started;
    }

    Map<String, Object> tenantConfiguration(final Map<String, String> properties) {
      final Map<String, Object> configuration = new LinkedHashMap<>();
      configuration.put("name", "Key effect default");
      configuration.put("data.secondary-storage.type", "none");
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

    void awaitHealthy() {
      final var registry = broker.bean(DefaultPhysicalTenantRegistry.class);
      Awaitility.await("the default tenant is healthy")
          .atMost(TIMEOUT)
          .until(
              () ->
                  registry.health(DEFAULT).map(PhysicalTenantHealth::state).orElse(null)
                      == State.HEALTHY);
      try {
        lifecycle().awaitReady(DEFAULT).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      } catch (final Exception e) {
        throw new IllegalStateException(e);
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
