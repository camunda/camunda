/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant.cost;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import io.camunda.application.commons.rdbms.RdbmsSchemaInitializer;
import io.camunda.application.commons.search.SearchEngineSchemaInitializer;
import io.camunda.it.physicaltenant.cost.CostReport.Sample;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Measures what each physical tenant costs a node: retained heap, live threads, open database
 * connections, the instances of the classes that hold them and startup time, for a range of tenant
 * counts. The tenants are on RDBMS, or on Elasticsearch with {@code
 * -Dcamunda.pt.cost.storage=elasticsearch}. With {@code
 * -Dcamunda.pt.cost.idle-release-timeout=PT5S}, a third phase measures the node after its tenants
 * have been released for going unused. It is a measurement, not a regression test, so it only runs
 * when asked for:
 *
 * <pre>
 * ./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
 *   -Dit.test=PhysicalTenantCostHarnessIT -Dcamunda.pt.cost.harness=true \
 *   -Dcamunda.pt.cost.tenants=1,10,20,40,80 -Dcamunda.pt.cost.brokers=3 \
 *   -Dcamunda.pt.cost.label=after -Djvm.test.extra.args=-Xmx8g
 * </pre>
 *
 * <p>Each tenant has one partition with one replica, so on a cluster of several brokers each broker
 * hosts a share of the tenants, as in the load tests. All brokers run in this JVM, so the heap,
 * threads and connections are those of the whole cluster. The tenants' databases live in a separate
 * H2 process and do not count. Results go to {@code target/pt-cost}.
 */
@EnabledIfSystemProperty(named = "camunda.pt.cost.harness", matches = "true")
@Timeout(value = 60, unit = TimeUnit.MINUTES)
final class PhysicalTenantCostHarnessIT {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantCostHarnessIT.class);

  private static final int BROKERS = Integer.getInteger("camunda.pt.cost.brokers", 3);
  private static final String LABEL = System.getProperty("camunda.pt.cost.label", "current");
  private static final Duration SETTLE =
      Duration.parse(System.getProperty("camunda.pt.cost.settle", "PT5S"));
  private static final String POOL_IDLE_TIMEOUT =
      System.getProperty("camunda.pt.cost.pool-idle-timeout", "10m");
  private static final Path OUTPUT =
      Path.of(System.getProperty("camunda.pt.cost.output", "target/pt-cost"));
  private static final List<Integer> HEAP_DUMP_AT =
      Arrays.stream(System.getProperty("camunda.pt.cost.heap-dump-at", "").split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(Integer::valueOf)
          .toList();

  private static final boolean ELASTICSEARCH =
      "elasticsearch".equals(System.getProperty("camunda.pt.cost.storage", "rdbms"));
  private static final boolean SHARED_POOL = Boolean.getBoolean("camunda.pt.cost.shared-pool");
  private static final Duration IDLE_RELEASE_TIMEOUT =
      Duration.parse(System.getProperty("camunda.pt.cost.idle-release-timeout", "PT0S"));
  private static final List<String> INSTANCE_CLASSES =
      ELASTICSEARCH
          ? List.of(
              "org.elasticsearch.client.RestClient",
              "org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor",
              "org.opensearch.client.RestClient")
          : List.of(
              "com.zaxxer.hikari.pool.HikariPool",
              "org.apache.ibatis.session.Configuration",
              "org.apache.ibatis.mapping.MappedStatement");

  private static final CostReport REPORT =
      new CostReport(
          LABEL,
          OUTPUT,
          ELASTICSEARCH
              ? List.of("ES rest clients", "IO reactors", "OS rest clients")
              : List.of("Hikari pools", "MyBatis configurations", "mapped statements"));
  private static final Set<String> DATABASE_URLS = new LinkedHashSet<>();
  private static @Nullable H2TcpServer h2;
  private static @Nullable ElasticsearchContainer elasticsearch;

  @BeforeAll
  static void startDatabaseServer() throws Exception {
    if (!ELASTICSEARCH) {
      h2 = H2TcpServer.start(Path.of("target", "pt-cost-h2.log").toAbsolutePath());
    }
  }

  @AfterAll
  static void writeReportAndStopDatabaseServer() throws Exception {
    try {
      REPORT.writeSummary();
    } finally {
      if (h2 != null) {
        h2.close();
      }
    }
  }

  static Stream<Integer> tenantCounts() {
    return Arrays.stream(System.getProperty("camunda.pt.cost.tenants", "1,10,20,40,80").split(","))
        .map(String::trim)
        .map(Integer::valueOf);
  }

  @ParameterizedTest(name = "{0} tenants")
  @MethodSource("tenantCounts")
  void shouldMeasureTheCostOfIdlePhysicalTenants(final int tenantCount) throws Exception {
    // given
    DATABASE_URLS.clear();
    if (ELASTICSEARCH) {
      // a fresh node per tenant count, so the indices of the last count do not slow this one
      elasticsearch =
          TestSearchContainers.createDefaultElasticsearchContainer()
              .withEnv("cluster.max_shards_per_node", "100000");
      elasticsearch.start();
    }
    final var tenants = tenants(tenantCount);
    final var baseline = JvmFootprint.capture();

    // when
    final long startedAt = System.nanoTime();
    try (final var cluster =
        TestCluster.builder()
            .withName("pt-cost-" + tenantCount)
            .withBrokersCount(BROKERS)
            .withEmbeddedGateway(true)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .useRecordingExporter(false)
            .withBrokerConfig(
                broker ->
                    prefixTables(
                        tenants.configure(
                            broker
                                .withUnauthenticatedAccess()
                                .withCreateSchema(ELASTICSEARCH)
                                .withProperty(
                                    "camunda.data.secondary-storage.rdbms.connection-pool.idle-timeout",
                                    POOL_IDLE_TIMEOUT)
                                .withProperty(
                                    "camunda.data.secondary-storage.rdbms.idle-release-timeout",
                                    IDLE_RELEASE_TIMEOUT.toString())
                                .withProperty(
                                    "camunda.data.secondary-storage.elasticsearch.idle-release-timeout",
                                    IDLE_RELEASE_TIMEOUT.toString())
                                .withProperty(
                                    "camunda.data.secondary-storage.rdbms.connection-pool.shared",
                                    Boolean.toString(SHARED_POOL))
                                .withUnifiedConfig(
                                    config -> {
                                      config.getSystem().setCpuThreadCount(2);
                                      config.getSystem().setIoThreadCount(2);
                                      config
                                          .getData()
                                          .getPrimaryStorage()
                                          .getRocksDb()
                                          .setMemoryLimit(DataSize.ofGigabytes(16));
                                    })),
                        tenants))
            .build()) {
      cluster.start().await(TestHealthProbe.READY, Duration.ofMinutes(10));
      cluster.brokers().values().forEach(PhysicalTenantCostHarnessIT::awaitEverySchema);
      final long startupMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
      awaitThreadsSettled();

      // then
      record(baseline, "idle", tenantCount, startupMillis);

      final var gateway = cluster.brokers().values().iterator().next();
      for (final var tenant : tenants.tenantIds()) {
        try (final var client =
            tenants.newClientBuilder(gateway, tenant).preferRestOverGrpc(true).build()) {
          Awaitility.await("a query of tenant " + tenant + " succeeds")
              .atMost(Duration.ofMinutes(1))
              .ignoreExceptions()
              .untilAsserted(
                  () ->
                      assertThat(client.newProcessInstanceSearchRequest().send().join())
                          .isNotNull());
        }
      }
      awaitThreadsSettled();
      record(baseline, "queried-on-one-gateway", tenantCount, startupMillis);

      if (IDLE_RELEASE_TIMEOUT.isPositive()) {
        // the release check runs at half the timeout, so two timeouts cover the last use plus one
        // check, whatever the phase of the check
        Awaitility.await("the idle release timeout passes")
            .pollDelay(IDLE_RELEASE_TIMEOUT.multipliedBy(2))
            .atMost(IDLE_RELEASE_TIMEOUT.multipliedBy(3))
            .until(() -> true);
        record(baseline, "after-idle-release", tenantCount, startupMillis);
      }
    } finally {
      if (elasticsearch != null) {
        elasticsearch.stop();
        elasticsearch = null;
      }
    }
  }

  private static void record(
      final JvmFootprint baseline, final String phase, final int tenants, final long startupMillis)
      throws Exception {
    final var cost = JvmFootprint.capture().minus(baseline);
    final var sample =
        new Sample(
            phase,
            BROKERS,
            tenants,
            startupMillis,
            cost.heapUsedBytes(),
            cost.threads(),
            openConnections(),
            cost.instances(INSTANCE_CLASSES.get(0)),
            cost.instances(INSTANCE_CLASSES.get(1)),
            cost.instances(INSTANCE_CLASSES.get(2)),
            cost.topByBytes(20).stream()
                .map(
                    e ->
                        "| %s | %d | %d |"
                            .formatted(e.getKey(), e.getValue().instances(), e.getValue().bytes()))
                .toList());
    REPORT.add(sample);
    LOG.info("Physical tenant cost sample {}", sample);
    if (HEAP_DUMP_AT.contains(tenants)) {
      dumpHeap(phase, tenants);
    }
  }

  private static void dumpHeap(final String phase, final int tenants) throws Exception {
    final var file = OUTPUT.resolve("pt-cost-%s-%d-%s.hprof".formatted(LABEL, tenants, phase));
    Files.createDirectories(OUTPUT);
    Files.deleteIfExists(file);
    ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
        .dumpHeap(file.toAbsolutePath().toString(), true);
    LOG.info("Wrote heap dump {}", file.toAbsolutePath());
  }

  private static PhysicalTenantsITHelper tenants(final int tenantCount) {
    final var builder = PhysicalTenantsITHelper.builder();
    final var sharedDatabase = SHARED_POOL ? databaseUrl("shared") : null;
    IntStream.range(0, tenantCount)
        .mapToObj(i -> i == 0 ? PhysicalTenantsITHelper.DEFAULT_TENANT_ID : "pt%03d".formatted(i))
        .forEach(
            tenant ->
                builder.withTenant(
                    tenant,
                    sharedDatabase == null
                        ? storage(tenant)
                        : Storage.rdbms(sharedDatabase, "sa", ""),
                    1));
    return builder.build();
  }

  /**
   * On one shared database, as in the load tests, each tenant's tables carry its own prefix.
   * Without a shared pool, each tenant has its own database.
   */
  private static void prefixTables(
      final TestStandaloneBroker broker, final PhysicalTenantsITHelper tenants) {
    if (!SHARED_POOL) {
      return;
    }
    for (final var tenant : tenants.tenantIds()) {
      final var prefix = tenant.toUpperCase(Locale.ROOT) + "_";
      if (PhysicalTenantsITHelper.DEFAULT_TENANT_ID.equals(tenant)) {
        broker.withProperty("camunda.data.secondary-storage.rdbms.prefix", prefix);
      } else {
        broker.withPtConfig(
            tenant,
            camunda -> camunda.getData().getSecondaryStorage().getRdbms().setPrefix(prefix));
      }
    }
  }

  private static Storage storage(final String tenant) {
    if (ELASTICSEARCH) {
      return Storage.elasticsearch(
          "http://" + Objects.requireNonNull(elasticsearch).getHttpHostAddress(), tenant);
    }
    return Storage.rdbms(databaseUrl(tenant), "sa", "");
  }

  private static String databaseUrl(final String tenant) {
    final var url = Objects.requireNonNull(h2).newDatabaseUrl(tenant);
    DATABASE_URLS.add(url);
    return url;
  }

  private static long openConnections() throws Exception {
    if (ELASTICSEARCH) {
      return openHttpConnections();
    }
    long open = 0;
    for (final var url : DATABASE_URLS) {
      open += Objects.requireNonNull(h2).openSessions(url);
    }
    return open;
  }

  /** The HTTP connections open on the Elasticsearch node, as its node stats count them. */
  private static long openHttpConnections() throws Exception {
    final var uri =
        URI.create(
            "http://"
                + Objects.requireNonNull(elasticsearch).getHttpHostAddress()
                + "/_nodes/stats/http?filter_path=nodes.*.http.current_open");
    try (final var http = HttpClient.newHttpClient()) {
      final var body =
          http.send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString()).body();
      final var matcher = Pattern.compile("\"current_open\":(\\d+)").matcher(body);
      // this request's own connection is one of them
      return matcher.find() ? Long.parseLong(matcher.group(1)) - 1 : -1;
    }
  }

  private static void awaitEverySchema(final TestStandaloneBroker broker) {
    Awaitility.await("every tenant's schema is initialized on " + broker.nodeId())
        .atMost(Duration.ofMinutes(10))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () ->
                (ELASTICSEARCH
                        ? broker.bean(SearchEngineSchemaInitializer.class).statuses()
                        : broker.bean(RdbmsSchemaInitializer.class).statuses())
                    .values().stream().allMatch(status -> status.state() == State.INITIALIZED));
  }

  private static void awaitThreadsSettled() {
    if (IDLE_RELEASE_TIMEOUT.isPositive()) {
      // exporters recreate their tenants after each release, so the threads do not settle
      Awaitility.await("the node settles")
          .pollDelay(SETTLE)
          .atMost(SETTLE.plusSeconds(1))
          .until(() -> true);
      return;
    }
    final var last = new AtomicInteger(-1);
    Awaitility.await("the live thread count settles")
        .atMost(SETTLE.plus(Duration.ofMinutes(2)))
        .pollInterval(Duration.ofMillis(500))
        .during(SETTLE)
        .until(
            () -> {
              final int now = ManagementFactory.getThreadMXBean().getThreadCount();
              return last.getAndSet(now) == now;
            });
  }
}
