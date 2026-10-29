/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant.cost;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import io.camunda.application.commons.rdbms.RdbmsSchemaInitializer;
import io.camunda.it.physicaltenant.cost.CostReport.Sample;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Measures what each physical tenant costs a node on RDBMS: retained heap, live threads, open JDBC
 * connections, started Hikari pools, MyBatis graphs and startup time, for a range of tenant counts.
 * It is a measurement, not a regression test, so it only runs when asked for:
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

  private static final CostReport REPORT = new CostReport(LABEL, OUTPUT);
  private static final List<String> DATABASE_URLS = new ArrayList<>();
  private static H2TcpServer h2;

  @BeforeAll
  static void startDatabaseServer() throws Exception {
    h2 = H2TcpServer.start(Path.of("target", "pt-cost-h2.log").toAbsolutePath());
  }

  @AfterAll
  static void writeReportAndStopDatabaseServer() throws Exception {
    try {
      REPORT.writeSummary();
    } finally {
      h2.close();
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
                    tenants.configure(
                        broker
                            .withUnauthenticatedAccess()
                            .withProperty(
                                "camunda.data.secondary-storage.rdbms.connection-pool.idle-timeout",
                                POOL_IDLE_TIMEOUT)
                            .withUnifiedConfig(
                                config -> {
                                  config.getSystem().setCpuThreadCount(2);
                                  config.getSystem().setIoThreadCount(2);
                                  config
                                      .getData()
                                      .getPrimaryStorage()
                                      .getRocksDb()
                                      .setMemoryLimit(DataSize.ofGigabytes(16));
                                })))
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
            cost.instances("com.zaxxer.hikari.pool.HikariPool"),
            cost.instances("org.apache.ibatis.session.Configuration"),
            cost.instances("org.apache.ibatis.mapping.MappedStatement"),
            cost.topByBytes(20).stream()
                .map(
                    e ->
                        "| %s | %d | %d |"
                            .formatted(e.getKey(), e.getValue().instances(), e.getValue().bytes()))
                .toList());
    REPORT.add(sample);
    LOG.info("Physical tenant cost sample {}", sample);
  }

  private static PhysicalTenantsITHelper tenants(final int tenantCount) {
    final var builder = PhysicalTenantsITHelper.builder();
    IntStream.range(0, tenantCount)
        .mapToObj(i -> i == 0 ? PhysicalTenantsITHelper.DEFAULT_TENANT_ID : "pt%03d".formatted(i))
        .forEach(
            tenant -> builder.withTenant(tenant, Storage.rdbms(databaseUrl(tenant), "sa", ""), 1));
    return builder.build();
  }

  private static String databaseUrl(final String tenant) {
    final var url = h2.newDatabaseUrl(tenant);
    DATABASE_URLS.add(url);
    return url;
  }

  private static long openConnections() throws Exception {
    long open = 0;
    for (final var url : DATABASE_URLS) {
      open += h2.openSessions(url);
    }
    return open;
  }

  private static void awaitEverySchema(final TestStandaloneBroker broker) {
    Awaitility.await("every tenant's schema is initialized on " + broker.nodeId())
        .atMost(Duration.ofMinutes(10))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () ->
                broker.bean(RdbmsSchemaInitializer.class).statuses().values().stream()
                    .allMatch(status -> status.state() == State.INITIALIZED));
  }

  private static void awaitThreadsSettled() {
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
