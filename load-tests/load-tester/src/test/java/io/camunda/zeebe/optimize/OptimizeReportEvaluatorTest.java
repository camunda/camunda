/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.config.OptimizeProperties;
import io.camunda.zeebe.optimize.OptimizeApiClient.PageEvaluationResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.ReportEvaluationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OptimizeReportEvaluatorTest {

  private TestScheduledExecutor executor;
  private OptimizeApiClient apiClient;
  private OptimizeProperties props;
  private SimpleMeterRegistry registry;
  private OptimizeReportEvaluator evaluator;

  @BeforeEach
  void setUp() {
    executor = new TestScheduledExecutor();
    apiClient = mock(OptimizeApiClient.class);
    registry = new SimpleMeterRegistry();
    props = new OptimizeProperties();
    props.setProcessDefinitionKey("pd-static");
    props.setEvaluationInterval(Duration.ofMillis(50));
    props.setInitialDelay(Duration.ofMillis(10));
    evaluator = new OptimizeReportEvaluator(props, apiClient, executor, registry);
  }

  @AfterEach
  void tearDown() {
    evaluator.close();
  }

  @Test
  void shouldRunHomepageAndDetailedOnEachCycle() {
    // given
    when(apiClient.evaluateHomepage())
        .thenReturn(new PageEvaluationResult(200, 10L, List.of(report("rA", 200))));
    when(apiClient.evaluateDetailedPage("pd-static"))
        .thenReturn(new PageEvaluationResult(200, 15L, List.of(report("rD", 200))));

    // when
    evaluator.runOneCycleSafely();
    evaluator.runOneCycleSafely();

    // then
    verify(apiClient, times(2)).evaluateHomepage();
    verify(apiClient, times(2)).evaluateDetailedPage("pd-static");
  }

  @Test
  void shouldPublishLatencyAndStatusMetricsPerCycle() {
    // given - a failing report on the homepage, a successful one on the detailed page
    when(apiClient.evaluateHomepage())
        .thenReturn(new PageEvaluationResult(200, 10L, List.of(report("rA", 404))));
    when(apiClient.evaluateDetailedPage("pd-static"))
        .thenReturn(new PageEvaluationResult(200, 15L, List.of(report("rD", 200))));

    // when
    evaluator.runOneCycleSafely();

    // then - a request counter per (page, phase, status_code), including the 404
    assertThat(counter("homepage", "dashboard", "200")).isEqualTo(1.0);
    assertThat(counter("homepage", "report_evaluate", "404")).isEqualTo(1.0);
    assertThat(counter("detailed", "report_evaluate", "200")).isEqualTo(1.0);
    // and - latency timer tagged by report_id (n/a for dashboard fetch, real id per report)
    assertThat(
            registry
                .get("optimize.report.evaluation.latency")
                .tag("page", "detailed")
                .tag("phase", "dashboard")
                .tag("report_id", "n/a")
                .timer()
                .count())
        .isEqualTo(1L);
    assertThat(
            registry
                .get("optimize.report.evaluation.latency")
                .tag("page", "homepage")
                .tag("phase", "report_evaluate")
                .tag("report_id", "rA")
                .timer()
                .count())
        .isEqualTo(1L);
  }

  @Test
  void shouldSwallowExceptionsInCycle() {
    // given
    when(apiClient.evaluateHomepage())
        .thenThrow(new RuntimeException("boom"))
        .thenReturn(new PageEvaluationResult(200, 1L, List.of()));
    when(apiClient.evaluateDetailedPage("pd-static"))
        .thenReturn(new PageEvaluationResult(200, 1L, List.of()));

    // when - homepage throws on the first cycle (swallowed); detailed still runs, second is clean
    evaluator.runOneCycleSafely();
    evaluator.runOneCycleSafely();

    // then - second cycle still runs
    verify(apiClient, times(2)).evaluateHomepage();
    verify(apiClient, times(2)).evaluateDetailedPage("pd-static");
  }

  @Test
  void shouldDiscoverProcessDefinitionKeyWhenBlank() {
    // given
    props.setProcessDefinitionKey("");
    when(apiClient.fetchFirstProcessDefinitionKey()).thenReturn("pd-discovered");
    when(apiClient.evaluateHomepage()).thenReturn(new PageEvaluationResult(200, 1L, List.of()));
    when(apiClient.evaluateDetailedPage("pd-discovered"))
        .thenReturn(new PageEvaluationResult(200, 1L, List.of()));

    // when
    evaluator.runOneCycleSafely();
    evaluator.runOneCycleSafely();

    // then - the key is fetched once and cached for the second cycle
    verify(apiClient, times(1)).fetchFirstProcessDefinitionKey();
    verify(apiClient, times(2)).evaluateDetailedPage("pd-discovered");
  }

  @Test
  void shouldSkipDetailedPageWhenKeyResolutionFails() {
    // given
    props.setProcessDefinitionKey("");
    when(apiClient.fetchFirstProcessDefinitionKey())
        .thenThrow(new RuntimeException("no processes"));
    when(apiClient.evaluateHomepage()).thenReturn(new PageEvaluationResult(200, 1L, List.of()));

    // when
    evaluator.runOneCycleSafely();

    // then
    verify(apiClient, never()).evaluateDetailedPage(any());
  }

  @Test
  void shouldCancelScheduledTasksOnClose() {
    // given
    when(apiClient.evaluateHomepage()).thenReturn(new PageEvaluationResult(200, 1L, List.of()));
    when(apiClient.evaluateDetailedPage(any()))
        .thenReturn(new PageEvaluationResult(200, 1L, List.of()));
    evaluator.start();

    // when
    evaluator.close();

    // then
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(executor.isShutdown()).isTrue());
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThat(executor.futures())
                    .isNotEmpty()
                    .allSatisfy(f -> assertThat(f.isCancelled()).isTrue()));
  }

  private double counter(final String page, final String phase, final String status) {
    return registry
        .get("optimize.report.evaluation.requests")
        .tag("page", page)
        .tag("phase", phase)
        .tag("status_code", status)
        .counter()
        .count();
  }

  private static ReportEvaluationResult report(final String id, final int status) {
    return new ReportEvaluationResult(id, status, 5L, "{}");
  }

  private static final class TestScheduledExecutor extends ScheduledThreadPoolExecutor {
    private final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();

    TestScheduledExecutor() {
      super(1);
      setRemoveOnCancelPolicy(true);
    }

    @Override
    public ScheduledFuture<?> schedule(
        final Runnable command, final long delay, final TimeUnit unit) {
      final ScheduledFuture<?> future = super.schedule(command, delay, unit);
      futures.add(future);
      return future;
    }

    List<ScheduledFuture<?>> futures() {
      return futures;
    }
  }
}
