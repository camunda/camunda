/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalTenantsRdbmsTableRowCountMetricsTest {

  private static final Duration DEFAULT_CACHE_DURATION = Duration.ofMinutes(15);
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private TableMetricsMapper mapperA;
  private TableMetricsMapper mapperB;
  private MeterRegistry meterRegistry;
  private ExecutorService executor;
  private PhysicalTenantsRdbmsTableRowCountMetrics metrics;

  @BeforeEach
  void setUp() {
    mapperA = mock(TableMetricsMapper.class);
    mapperB = mock(TableMetricsMapper.class);
    meterRegistry = new SimpleMeterRegistry();
    // Single-threaded so a no-op task submitted after triggering a load can be used as a barrier:
    // once that no-op task completes, the earlier (async) load is guaranteed to have finished too.
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private void awaitPendingLoads() throws Exception {
    executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
  }

  private RdbmsTableRowCountProvider provider(
      final TableMetricsMapper mapper, final Duration cacheDuration) {
    return new RdbmsTableRowCountProvider(mapper, vendorProperties(), "", cacheDuration, executor);
  }

  private static VendorDatabaseProperties vendorProperties() {
    final var props = new Properties();
    props.put(VendorDatabaseProperties.DATABASE_ID, "h2");
    props.put("variableValue.previewSize", "8191");
    props.put("userCharColumn.size", "256");
    props.put("errorMessage.size", "4000");
    props.put("treePath.size", "8191");
    props.put("disableFkBeforeTruncate", "false");
    props.put("tableRowCount.strategy", "live");
    props.put("tableRowCount.identifierCase", "none");
    return new VendorDatabaseProperties(props);
  }

  @Test
  void shouldRegisterGaugesForAllTablesOfEachPhysicalTenant() {
    // given - h2 counts one table at a time
    when(mapperA.countSingleTableRows(anyString())).thenReturn(100L);
    when(mapperB.countSingleTableRows(anyString())).thenReturn(100L);
    metrics =
        new PhysicalTenantsRdbmsTableRowCountMetrics(
            Map.of(
                TENANT_A, provider(mapperA, DEFAULT_CACHE_DURATION),
                TENANT_B, provider(mapperB, DEFAULT_CACHE_DURATION)));

    // when
    metrics.bindTo(meterRegistry);

    // then
    for (final String physicalTenantId : new String[] {TENANT_A, TENANT_B}) {
      for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
        final Gauge gauge =
            meterRegistry
                .find("zeebe.rdbms.table.row.count")
                .tag("physicalTenant", physicalTenantId)
                .tag("table", tableName)
                .gauge();
        assertThat(gauge)
            .as("Gauge for tenant %s table %s should be registered", physicalTenantId, tableName)
            .isNotNull();
      }
    }
  }

  @Test
  void shouldReportRowCountsPerPhysicalTenantIndependently() throws Exception {
    // given
    when(mapperA.countSingleTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    when(mapperB.countSingleTableRows("PROCESS_INSTANCE")).thenReturn(7L);
    metrics =
        new PhysicalTenantsRdbmsTableRowCountMetrics(
            Map.of(
                TENANT_A, provider(mapperA, DEFAULT_CACHE_DURATION),
                TENANT_B, provider(mapperB, DEFAULT_CACHE_DURATION)));
    metrics.bindTo(meterRegistry);

    // when - the first read of each gauge only triggers the (async) load; wait for it to finish
    final Gauge gaugeA =
        meterRegistry
            .find("zeebe.rdbms.table.row.count")
            .tag("physicalTenant", TENANT_A)
            .tag("table", "PROCESS_INSTANCE")
            .gauge();
    final Gauge gaugeB =
        meterRegistry
            .find("zeebe.rdbms.table.row.count")
            .tag("physicalTenant", TENANT_B)
            .tag("table", "PROCESS_INSTANCE")
            .gauge();
    gaugeA.value();
    gaugeB.value();
    awaitPendingLoads();

    // then
    assertThat(gaugeA.value()).isEqualTo(42.0);
    assertThat(gaugeB.value()).isEqualTo(7.0);
  }

  @Test
  void shouldCloseOwnedExecutors() {
    // given
    final var executorA = Executors.newSingleThreadExecutor();
    final var executorB = Executors.newSingleThreadExecutor();
    metrics = new PhysicalTenantsRdbmsTableRowCountMetrics(Map.of(), List.of(executorA, executorB));

    // when
    metrics.close();

    // then
    assertThat(executorA.isShutdown()).isTrue();
    assertThat(executorB.isShutdown()).isTrue();
  }
}
