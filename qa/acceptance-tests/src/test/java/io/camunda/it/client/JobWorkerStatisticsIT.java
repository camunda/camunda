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
import static io.camunda.it.util.TestHelper.waitForJobWorkerStatistics;
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
public class JobWorkerStatisticsIT {

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
    // Deploy a process with sequential service tasks (taskA -> taskB -> taskC)
    deployResource(adminClient, "process/service_tasks_v1.bpmn");

    // Start 4 process instances to create 4 taskA jobs
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);

    // Wait for 4 taskA jobs to be available
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A), 4);

    // worker-1 completes 2 taskA jobs -> creates 2 taskB jobs
    activateAndCompleteJobs(adminClient, JOB_TYPE_A, WORKER_1, 2);
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_A), 2);

    // worker-2 fails 1 taskA job
    activateAndFailJobs(adminClient, JOB_TYPE_A, WORKER_2, 1, "Intentional failure for test");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_A), 1);

    // Wait for the 2 taskB jobs to be available (created after taskA completions)
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_B).state(JobState.CREATED), 2);

    // worker-1 completes 1 taskB job
    activateAndCompleteJobs(adminClient, JOB_TYPE_B, WORKER_1, 1);
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_B), 1);

    // worker-3 fails 1 taskB job
    activateAndFailJobs(adminClient, JOB_TYPE_B, WORKER_3, 1, "Intentional failure for test");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_B), 1);

    // Wait for metrics to be exported: taskA has worker-1 and worker-2; taskB has worker-1 and
    // worker-3
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).hasSizeGreaterThanOrEqualTo(2));
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> assertThat(stats.items()).hasSizeGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldReturnWorkerStatisticsForJobTypeA(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // taskA: worker-1 completed 2, worker-2 failed 1, 1 job still created
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSize(2);

          // Results sorted by worker ASC
          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(2L);
          assertThat(worker1Stats.getCompleted().getLastUpdatedAt()).isNotNull();
          assertThat(worker1Stats.getFailed().getCount()).isZero();
          assertThat(worker1Stats.getFailed().getLastUpdatedAt()).isNull();

          final var worker2Stats = stats.items().get(1);
          assertThat(worker2Stats.getWorker()).isEqualTo(WORKER_2);
          assertThat(worker2Stats.getCompleted().getCount()).isZero();
          assertThat(worker2Stats.getCompleted().getLastUpdatedAt()).isNull();
          assertThat(worker2Stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(worker2Stats.getFailed().getLastUpdatedAt()).isNotNull();
        });
  }

  @Test
  void shouldReturnWorkerStatisticsForJobTypeB(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // taskB: worker-1 completed 1, worker-3 failed 1
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> {
          assertThat(stats.items()).hasSize(2);

          // Results sorted by worker ASC: worker-1 < worker-3
          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(worker1Stats.getCompleted().getLastUpdatedAt()).isNotNull();
          assertThat(worker1Stats.getFailed().getCount()).isZero();
          assertThat(worker1Stats.getFailed().getLastUpdatedAt()).isNull();

          final var worker3Stats = stats.items().get(1);
          assertThat(worker3Stats.getWorker()).isEqualTo(WORKER_3);
          assertThat(worker3Stats.getCompleted().getCount()).isZero();
          assertThat(worker3Stats.getCompleted().getLastUpdatedAt()).isNull();
          assertThat(worker3Stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(worker3Stats.getFailed().getLastUpdatedAt()).isNotNull();
        });
  }

  @Test
  void shouldNotShowTaskAWorkersForTaskBQuery(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // worker-2 only processed taskA jobs — must not appear in taskB worker stats
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> assertThat(stats.items()).extracting("worker").doesNotContain(WORKER_2));
  }

  @Test
  void shouldNotShowTaskBWorkersForTaskAQuery(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // worker-3 only processed taskB jobs — must not appear in taskA worker stats
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).extracting("worker").doesNotContain(WORKER_3));
  }

  @Test
  void shouldShowWorker1InBothJobTypeQueries(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // worker-1 processed both taskA (2 completed) and taskB (1 completed) — must appear in both
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          final var worker1Stats =
              stats.items().stream()
                  .filter(w -> WORKER_1.equals(w.getWorker()))
                  .findFirst()
                  .orElseThrow();
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(2L);
          assertThat(worker1Stats.getFailed().getCount()).isZero();
        });

    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> {
          final var worker1Stats =
              stats.items().stream()
                  .filter(w -> WORKER_1.equals(w.getWorker()))
                  .findFirst()
                  .orElseThrow();
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(worker1Stats.getFailed().getCount()).isZero();
        });
  }

  @Test
  void shouldReturnEmptyForNonExistentJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "non-existent-job-type",
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldPaginateWorkerStatisticsForTaskA(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page: worker-1 (ORDER BY worker ASC)
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getWorker()).isEqualTo(WORKER_1);
          endCursor.set(stats.page().endCursor());
        });

    // Second page: worker-2
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1).after(endCursor.get()),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getWorker()).isEqualTo(WORKER_2);
        });
  }

  @Test
  void shouldPaginateWorkerStatisticsForTaskB(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page: worker-1 (ORDER BY worker ASC)
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        p -> p.limit(1),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getWorker()).isEqualTo(WORKER_1);
          endCursor.set(stats.page().endCursor());
        });

    // Second page: worker-3
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        p -> p.limit(1).after(endCursor.get()),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getWorker()).isEqualTo(WORKER_3);
        });
  }

  @Test
  void shouldReturnEmptyWhenTimeRangeExcludesAllMetrics(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(30),
        NOW.minusDays(29),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldVerifyResultsAreSortedByWorkerForTaskA(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSize(2);
          assertThat(stats.items().get(0).getWorker()).isEqualTo(WORKER_1);
          assertThat(stats.items().get(1).getWorker()).isEqualTo(WORKER_2);
        });
  }

  @Test
  void shouldVerifyResultsAreSortedByWorkerForTaskB(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> {
          assertThat(stats.items()).hasSize(2);
          assertThat(stats.items().get(0).getWorker()).isEqualTo(WORKER_1);
          assertThat(stats.items().get(1).getWorker()).isEqualTo(WORKER_3);
        });
  }
}
