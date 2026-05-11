/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.config.OptimizeProperties;
import io.camunda.zeebe.metrics.OptimizeMetricsDoc;
import io.camunda.zeebe.metrics.OptimizeMetricsDoc.OptimizeMetricKeyNames;
import io.camunda.zeebe.optimize.OptimizeApiClient.DetailedPageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.HomepageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.OptimizeAuthException;
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

  private SimpleMeterRegistry meterRegistry;
  private TestScheduledExecutor executor;
  private OptimizeApiClient apiClient;
  private OptimizeProperties props;
  private OptimizeReportEvaluator evaluator;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    executor = new TestScheduledExecutor();
    apiClient = mock(OptimizeApiClient.class);
    props = new OptimizeProperties();
    props.setEnabled(true);
    props.setProcessDefinitionKey("pd-static");
    props.setEvaluationInterval(Duration.ofMillis(50));
    props.setInitialDelay(Duration.ofMillis(10));
    props.setAuthRetryDelay(Duration.ofMillis(1));
    props.setAuthRetryMaxAttempts(3);
  }

  @AfterEach
  void tearDown() {
    if (evaluator != null) {
      evaluator.close();
    }
    meterRegistry.close();
  }

  @Test
  void shouldRecordTimerForSuccessfulReports() {
    // given
    when(apiClient.evaluateHomepage())
        .thenReturn(
            new HomepageResult(
                200,
                10L,
                List.of(
                    new ReportEvaluationResult("rA", 200, 5L, "{}"),
                    new ReportEvaluationResult("rB", 200, 7L, "{}"))));
    when(apiClient.evaluateDetailedPage("pd-static"))
        .thenReturn(
            new DetailedPageResult(
                200,
                15L,
                List.of(new ReportEvaluationResult("rD", 200, 6L, "{}")),
                List.of(new ReportEvaluationResult("rD", 200, 8L, "{}"))));

    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);

    // when
    evaluator.runOneCycleSafely();

    // then
    assertThat(
            meterRegistry
                .get(OptimizeMetricsDoc.REPORT_EVALUATION_LATENCY.getName())
                .tag(OptimizeMetricKeyNames.PAGE.asString(), "homepage")
                .tag(OptimizeMetricKeyNames.PHASE.asString(), "report_evaluate")
                .tag(OptimizeMetricKeyNames.REPORT_ID.asString(), "rA")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(
            meterRegistry
                .get(OptimizeMetricsDoc.REPORT_EVALUATION_LATENCY.getName())
                .tag(OptimizeMetricKeyNames.PAGE.asString(), "detailed")
                .tag(OptimizeMetricKeyNames.PHASE.asString(), "detailed_evaluate")
                .tag(OptimizeMetricKeyNames.REPORT_ID.asString(), "rD")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(
            meterRegistry.find(OptimizeMetricsDoc.REPORT_EVALUATION_FAILURES.getName()).counters())
        .isEmpty();
  }

  @Test
  void shouldRecordFailureCounterForNon2xx() {
    // given
    when(apiClient.evaluateHomepage())
        .thenReturn(
            new HomepageResult(
                200, 10L, List.of(new ReportEvaluationResult("rFail", 500, 3L, "{}"))));
    when(apiClient.evaluateDetailedPage("pd-static"))
        .thenReturn(new DetailedPageResult(200, 5L, List.of(), List.of()));

    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);

    // when
    evaluator.runOneCycleSafely();

    // then
    assertThat(
            meterRegistry
                .get(OptimizeMetricsDoc.REPORT_EVALUATION_FAILURES.getName())
                .tag(OptimizeMetricKeyNames.PAGE.asString(), "homepage")
                .tag(OptimizeMetricKeyNames.PHASE.asString(), "report_evaluate")
                .tag(OptimizeMetricKeyNames.STATUS_CODE.asString(), "500")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldRetryAuthOnStartUpToMaxAttempts() {
    // given
    doThrow(new OptimizeAuthException("nope", 503))
        .doThrow(new OptimizeAuthException("nope", 503))
        .doNothing()
        .when(apiClient)
        .authenticateWithPasswordGrant();
    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);

    // when
    evaluator.authenticateWithRetry();

    // then
    verify(apiClient, times(3)).authenticateWithPasswordGrant();
    assertThat(
            meterRegistry
                .get(OptimizeMetricsDoc.AUTH_FAILURES.getName())
                .tag(OptimizeMetricKeyNames.REASON.asString(), "http_5xx")
                .counter()
                .count())
        .isEqualTo(2.0);
  }

  @Test
  void shouldFailFastWhenAuthExceedsMaxAttempts() {
    // given
    props.setAuthRetryMaxAttempts(2);
    doThrow(new OptimizeAuthException("nope", 401)).when(apiClient).authenticateWithPasswordGrant();
    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);

    // when / then
    assertThatThrownBy(() -> evaluator.authenticateWithRetry())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("after 2 attempts");
    verify(apiClient, times(2)).authenticateWithPasswordGrant();
    assertThat(
            meterRegistry
                .get(OptimizeMetricsDoc.AUTH_FAILURES.getName())
                .tag(OptimizeMetricKeyNames.REASON.asString(), "http_4xx")
                .counter()
                .count())
        .isEqualTo(2.0);
  }

  @Test
  void shouldDiscoverProcessDefinitionKeyWhenBlank() {
    // given
    props.setProcessDefinitionKey("");
    when(apiClient.fetchFirstProcessDefinitionKey()).thenReturn("pd-discovered");
    when(apiClient.evaluateHomepage()).thenReturn(new HomepageResult(200, 1L, List.of()));
    when(apiClient.evaluateDetailedPage("pd-discovered"))
        .thenReturn(new DetailedPageResult(200, 1L, List.of(), List.of()));

    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);

    // when
    evaluator.runOneCycleSafely();
    evaluator.runOneCycleSafely();

    // then - fetchFirst called only once, cached afterwards
    verify(apiClient, times(1)).fetchFirstProcessDefinitionKey();
    verify(apiClient, times(2)).evaluateDetailedPage("pd-discovered");
  }

  @Test
  void shouldSkipDetailedPageWhenKeyResolutionFails() {
    // given
    props.setProcessDefinitionKey("");
    when(apiClient.fetchFirstProcessDefinitionKey())
        .thenThrow(new RuntimeException("no processes"));
    when(apiClient.evaluateHomepage()).thenReturn(new HomepageResult(200, 1L, List.of()));
    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);

    // when
    evaluator.runOneCycleSafely();

    // then
    verify(apiClient, never()).evaluateDetailedPage(any());
  }

  @Test
  void shouldCancelScheduledTasksOnClose() {
    // given
    doNothing().when(apiClient).authenticateWithPasswordGrant();
    when(apiClient.evaluateHomepage()).thenReturn(new HomepageResult(200, 1L, List.of()));
    when(apiClient.evaluateDetailedPage(any()))
        .thenReturn(new DetailedPageResult(200, 1L, List.of(), List.of()));
    props.setProcessDefinitionKey("pd-static");
    evaluator = new OptimizeReportEvaluator(props, apiClient, meterRegistry, executor);
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

  private static final class TestScheduledExecutor extends ScheduledThreadPoolExecutor {
    private final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();

    TestScheduledExecutor() {
      super(1);
      setRemoveOnCancelPolicy(true);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
      final ScheduledFuture<?> future =
          super.scheduleAtFixedRate(command, initialDelay, period, unit);
      futures.add(future);
      return future;
    }

    List<ScheduledFuture<?>> futures() {
      return futures;
    }
  }
}
