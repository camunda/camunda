/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.TableMetricsMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RdbmsTableRowCountProviderTest {

  private static final Duration DEFAULT_CACHE_DURATION = Duration.ofMinutes(15);

  private TableMetricsMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = mock(TableMetricsMapper.class);
  }

  @Test
  void shouldReturnRowCountFromMapper() {
    // given
    when(mapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION);

    // when
    final long rowCount = provider.getRowCount("PROCESS_INSTANCE");

    // then
    assertThat(rowCount).isEqualTo(42L);
  }

  @Test
  void shouldCacheRowCountWithinCacheDuration() {
    // given
    when(mapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    final var provider = new RdbmsTableRowCountProvider(mapper, Duration.ofHours(1));

    // when - request the row count multiple times
    provider.getRowCount("PROCESS_INSTANCE");
    provider.getRowCount("PROCESS_INSTANCE");
    provider.getRowCount("PROCESS_INSTANCE");

    // then - mapper should only be called once due to caching
    verify(mapper, times(1)).countTableRows("PROCESS_INSTANCE");
  }

  @Test
  void shouldReturnNegativeOneOnMapperException() {
    // given
    when(mapper.countTableRows("PROCESS_INSTANCE"))
        .thenThrow(new RuntimeException("Database error"));
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION);

    // when
    final long rowCount = provider.getRowCount("PROCESS_INSTANCE");

    // then
    assertThat(rowCount).isEqualTo(-1L);
  }

  @Test
  void shouldReturnNegativeOneForUnknownTable() {
    // given
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION);

    // when
    final long rowCount = provider.getRowCount("UNKNOWN_TABLE");

    // then - should return -1 and NOT call the mapper (validation prevents SQL injection)
    assertThat(rowCount).isEqualTo(-1L);
    verify(mapper, times(0)).countTableRows("UNKNOWN_TABLE");
  }
}
