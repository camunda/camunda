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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RdbmsTableRowCountMetricsTest {

  private static final Duration DEFAULT_CACHE_DURATION = Duration.ofMinutes(15);

  private TableMetricsMapper tableMetricsMapper;
  private MeterRegistry meterRegistry;
  private RdbmsTableRowCountMetrics metrics;

  @BeforeEach
  void setUp() {
    tableMetricsMapper = mock(TableMetricsMapper.class);
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void shouldRegisterGaugesForAllTables() {
    // given
    when(tableMetricsMapper.countTableRows(anyString())).thenReturn(100L);
    metrics = new RdbmsTableRowCountMetrics(tableMetricsMapper, DEFAULT_CACHE_DURATION);

    // when
    metrics.bindTo(meterRegistry);

    // then
    for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
      final Gauge gauge =
          meterRegistry.find("zeebe.rdbms.table.row.count").tag("table", tableName).gauge();
      assertThat(gauge).as("Gauge for table %s should be registered", tableName).isNotNull();
    }
  }

  @Test
  void shouldReturnRowCountFromMapper() {
    // given
    when(tableMetricsMapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    metrics = new RdbmsTableRowCountMetrics(tableMetricsMapper, DEFAULT_CACHE_DURATION);
    metrics.bindTo(meterRegistry);

    // when
    final Gauge gauge =
        meterRegistry.find("zeebe.rdbms.table.row.count").tag("table", "PROCESS_INSTANCE").gauge();

    // then
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(42.0);
  }

  @Test
  void shouldCacheRowCountWithinCacheDuration() {
    // given
    when(tableMetricsMapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    // Use a very long cache duration to ensure caching
    metrics = new RdbmsTableRowCountMetrics(tableMetricsMapper, Duration.ofHours(1));
    metrics.bindTo(meterRegistry);

    final Gauge gauge =
        meterRegistry.find("zeebe.rdbms.table.row.count").tag("table", "PROCESS_INSTANCE").gauge();

    // when - access gauge value multiple times
    gauge.value();
    gauge.value();
    gauge.value();

    // then - mapper should only be called once due to caching
    verify(tableMetricsMapper, times(1)).countTableRows("PROCESS_INSTANCE");
  }

  @Test
  void shouldReturnCachedValueWithGetRowCount() {
    // given
    when(tableMetricsMapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    metrics = new RdbmsTableRowCountMetrics(tableMetricsMapper, Duration.ofHours(1));
    metrics.bindTo(meterRegistry);

    // when
    final long rowCount = metrics.getRowCount("PROCESS_INSTANCE");

    // then
    assertThat(rowCount).isEqualTo(42L);
  }

  @Test
  void shouldHandleMapperException() {
    // given
    when(tableMetricsMapper.countTableRows("PROCESS_INSTANCE"))
        .thenThrow(new RuntimeException("Database error"));
    metrics = new RdbmsTableRowCountMetrics(tableMetricsMapper, DEFAULT_CACHE_DURATION);
    metrics.bindTo(meterRegistry);

    // when
    final Gauge gauge =
        meterRegistry.find("zeebe.rdbms.table.row.count").tag("table", "PROCESS_INSTANCE").gauge();

    // then - should return -1 on error
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(-1.0);
  }

  @Test
  void shouldReturnNegativeOneForUnknownTable() {
    // given
    metrics = new RdbmsTableRowCountMetrics(tableMetricsMapper, DEFAULT_CACHE_DURATION);
    metrics.bindTo(meterRegistry);

    // when
    final long rowCount = metrics.getRowCount("UNKNOWN_TABLE");

    // then - should return -1 and NOT call the mapper (validation prevents SQL injection)
    assertThat(rowCount).isEqualTo(-1L);
    verify(tableMetricsMapper, times(0)).countTableRows("UNKNOWN_TABLE");
  }
}
