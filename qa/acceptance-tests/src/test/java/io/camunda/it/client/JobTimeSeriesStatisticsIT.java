/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.activateAndCompleteJobs;
import static io.camunda.it.util.TestHelper.activateAndFailJobs;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobTimeSeriesStatistics;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest(envVars = "ZEEBE_BROKER_EXPERIMENTAL_ENGINE_JOBMETRICS_EXPORTINTERVAL=PT2S")
public class JobTimeSeriesStatisticsIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.exportInterval", EXPORT_INTERVAL);

  private static final String ADMIN = "admin";
  private static final String JOB_TYPE_A = "taskA";
  private static final String JOB_TYPE_B = "taskB";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String WORKER_1 = "worker-1";
  private static final String WORKER_2 = "worker-2";
  private static final String WORKER_3 = "worker-3";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws InterruptedException {
    deployResource(adminClient, "process/service_tasks_v1.bpmn");

    // Start 4 process instances → 4 taskA jobs
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);

    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A), 4);

    // worker-1 completes 2 taskA → creates 2 taskB jobs
    activateAndCompleteJobs(adminClient, JOB_TYPE_A, WORKER_1, 2);
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_A), 2);

    // worker-2 fails 1 taskA
    activateAndFailJobs(adminClient, JOB_TYPE_A, WORKER_2, 1, "Intentional failure for test");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_A), 1);

    // Wait for 2 taskB jobs to appear
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_B).state(JobState.CREATED), 2);

    // worker-1 completes 1 taskB; worker-3 fails 1 taskB
    activateAndCompleteJobs(adminClient, JOB_TYPE_B, WORKER_1, 1);
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_B), 1);
    activateAndFailJobs(adminClient, JOB_TYPE_B, WORKER_3, 1, "Intentional failure for test");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_B), 1);

    // Wait until at least one time-series bucket is available for both job types
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isNotEmpty());
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> assertThat(stats.items()).isNotEmpty());
  }

  @Test
  void shouldReturnTimeSeriesBucketsForJobTypeA(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();

          // All buckets combined should reflect the total completed and failed counts
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(2L);
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnTimeSeriesBucketsForJobTypeB(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> {
          assertThat(stats.items()).isNotEmpty();

          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(1L);
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnBucketsOrderedAscendingByTime(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          for (int i = 1; i < stats.items().size(); i++) {
            assertThat(stats.items().get(i - 1).getTime())
                .isBeforeOrEqualTo(stats.items().get(i).getTime());
          }
        });
  }

  @Test
  void shouldReturnEmptyForNonExistentJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "non-existent-job-type",
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenTimeRangeExcludesAllMetrics(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(30),
        NOW.minusDays(29),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldReturnFirstPageOfTimeSeries(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page of size 1 — should return exactly one bucket
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.page().endCursor()).isNotNull();
          endCursor.set(stats.page().endCursor());
        });
  }

  @Test
  void shouldReturnConsistentTotalsWithExplicitMinuteResolution(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // A 1-minute resolution is the minimum allowed; totals must still be correct regardless of
    // how many buckets the data falls into.
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        Duration.ofMinutes(1),
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(2L);
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnConsistentTotalsWithHourResolution(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // A 1-hour bucket should aggregate all test activity into a single bucket (tests run in < 1h).
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        Duration.ofHours(1),
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(2L);
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnConsistentTotalsWithDayResolution(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // A 1-day bucket collapses everything into at most one bucket; totals must still hold.
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        Duration.ofDays(1),
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(2L);
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldProduceFewOrEqualBucketsWithCoarserResolution(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // A coarser resolution (1h) must yield fewer or equal buckets than a finer one (1min)
    // for the same time window.
    final var fineRef = new java.util.concurrent.atomic.AtomicInteger(0);
    final var coarseRef = new java.util.concurrent.atomic.AtomicInteger(0);

    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        Duration.ofMinutes(1),
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          fineRef.set(stats.items().size());
        });

    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        Duration.ofHours(1),
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          coarseRef.set(stats.items().size());
        });

    assertThat(coarseRef.get()).isLessThanOrEqualTo(fineRef.get());
  }

  @Test
  void shouldReturnBucketsOrderedAscendingByTimeWithExplicitResolution(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        Duration.ofMinutes(5),
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          for (int i = 1; i < stats.items().size(); i++) {
            assertThat(stats.items().get(i - 1).getTime())
                .isBeforeOrEqualTo(stats.items().get(i).getTime());
          }
        });
  }

  @Test
  void shouldReturnSameTotalsRegardlessOfResolution(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // Totals summed across all buckets must be identical for both job types regardless of the
    // chosen resolution.
    for (final Duration resolution :
        List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofHours(1))) {
      waitForJobTimeSeriesStatistics(
          adminClient,
          NOW.minusDays(1),
          NOW.plusDays(1),
          JOB_TYPE_B,
          resolution,
          stats -> {
            assertThat(stats.items()).isNotEmpty();
            final long totalCompleted =
                stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
            final long totalFailed =
                stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
            assertThat(totalCompleted)
                .as("completed count consistent for resolution %s", resolution)
                .isEqualTo(1L);
            assertThat(totalFailed)
                .as("failed count consistent for resolution %s", resolution)
                .isEqualTo(1L);
          });
    }
  }

  @Test
  void shouldNotIncludeJobTypeBDataForJobTypeAQuery(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // taskB: only 1 completed + 1 failed. taskA: 2 completed + 1 failed.
    // Querying taskA must not bleed taskB totals.
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(2L);
        });
  }
}
