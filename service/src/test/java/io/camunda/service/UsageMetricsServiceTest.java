/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UsageMetricsSearchClient;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class UsageMetricsServiceTest {

  private static final String READ_THREAD_NAME = "test-api-services-read";

  private ExecutorService executor;
  private UsageMetricsServices services;
  private UsageMetricsSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(UsageMetricsSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    executor =
        Executors.newFixedThreadPool(
            2, Thread.ofPlatform().name(READ_THREAD_NAME).daemon(true).factory());
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(executor);
    services =
        new UsageMetricsServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            executorProvider,
            null);
  }

  @AfterEach
  public void after() {
    executor.shutdownNow();
  }

  @Test
  public void shouldReturnUsageMetricsCount() {
    // given
    when(client.usageMetricStatistics(any()))
        .thenReturn(new UsageMetricStatisticsEntity(16, 14, 2, null));
    when(client.usageMetricTUStatistics(any()))
        .thenReturn(new UsageMetricTUStatisticsEntity(16, null));

    final UsageMetricsQuery searchQuery = usageMetricsQuery();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.items().getFirst())
        .isEqualTo(
            Tuple.of(
                new UsageMetricStatisticsEntity(16, 14, 2, null),
                new UsageMetricTUStatisticsEntity(16, null)));
  }

  @Test
  public void shouldRunReadsOnTheManagedExecutor() {
    // given
    final var statsReadThread = new AtomicReference<String>();
    final var tuStatsReadThread = new AtomicReference<String>();
    when(client.usageMetricStatistics(any()))
        .thenAnswer(
            invocation -> {
              statsReadThread.set(Thread.currentThread().getName());
              return new UsageMetricStatisticsEntity(16, 14, 2, null);
            });
    when(client.usageMetricTUStatistics(any()))
        .thenAnswer(
            invocation -> {
              tuStatsReadThread.set(Thread.currentThread().getName());
              return new UsageMetricTUStatisticsEntity(16, null);
            });

    // when
    services.search(usageMetricsQuery());

    // then
    assertThat(statsReadThread).hasValue(READ_THREAD_NAME);
    assertThat(tuStatsReadThread).hasValue(READ_THREAD_NAME);
  }

  private static UsageMetricsQuery usageMetricsQuery() {
    final var startTime =
        OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, OffsetDateTime.now().getOffset());
    final var endTime = OffsetDateTime.of(2023, 1, 2, 0, 0, 0, 0, OffsetDateTime.now().getOffset());
    return SearchQueryBuilders.usageMetricsSearchQuery()
        .filter(new UsageMetricsFilter.Builder().startTime(startTime).endTime(endTime).build())
        .build();
  }
}
