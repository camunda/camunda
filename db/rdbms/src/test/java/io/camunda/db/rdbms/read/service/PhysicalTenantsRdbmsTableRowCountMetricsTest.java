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
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalTenantsRdbmsTableRowCountMetricsTest {

  private static final Duration DEFAULT_CACHE_DURATION = Duration.ofMinutes(15);
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private TableMetricsMapper mapperA;
  private TableMetricsMapper mapperB;
  private MeterRegistry meterRegistry;
  private PhysicalTenantsRdbmsTableRowCountMetrics metrics;

  @BeforeEach
  void setUp() {
    mapperA = mock(TableMetricsMapper.class);
    mapperB = mock(TableMetricsMapper.class);
    meterRegistry = new SimpleMeterRegistry();
  }

  private static RdbmsTableRowCountProvider provider(
      final TableMetricsMapper mapper, final Duration cacheDuration) {
    return new RdbmsTableRowCountProvider(mapper, cacheDuration);
  }

  @Test
  void shouldRegisterGaugesForAllTablesOfEachPhysicalTenant() {
    // given
    when(mapperA.countTableRows(anyString())).thenReturn(100L);
    when(mapperB.countTableRows(anyString())).thenReturn(100L);
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
  void shouldReportRowCountsPerPhysicalTenantIndependently() {
    // given
    when(mapperA.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    when(mapperB.countTableRows("PROCESS_INSTANCE")).thenReturn(7L);
    metrics =
        new PhysicalTenantsRdbmsTableRowCountMetrics(
            Map.of(
                TENANT_A, provider(mapperA, DEFAULT_CACHE_DURATION),
                TENANT_B, provider(mapperB, DEFAULT_CACHE_DURATION)));
    metrics.bindTo(meterRegistry);

    // when
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

    // then
    assertThat(gaugeA.value()).isEqualTo(42.0);
    assertThat(gaugeB.value()).isEqualTo(7.0);
  }
}
