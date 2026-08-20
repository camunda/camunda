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
import static io.camunda.it.util.TestHelper.cancelInstance;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobTypeStatistics;
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
public class JobTypeStatisticsIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          // set exportInterval via properties because it is not yet supported in unified config
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.exportInterval", EXPORT_INTERVAL);

  private static final String ADMIN = "admin";
  private static final String JOB_TYPE_A = "taskA";
  private static final String JOB_TYPE_B = "taskB";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String WORKER_NAME = "testWorker";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws InterruptedException {
    // Deploy a process with service tasks (taskA, taskB, taskC)
    deployResource(adminClient, "process/service_tasks_v1.bpmn");

    // Start 2 process instances to create jobs
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);

    // Wait for the jobs to be created (2 instances x taskA = 2 taskA jobs initially)
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A), 2);

    // Complete 1 taskA job
    activateAndCompleteJobs(adminClient, JOB_TYPE_A, WORKER_NAME, 1);

    // Wait for completion to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_A), 1);

    // Fail 1 taskA job (the remaining one from instance 2)
    activateAndFailJobs(adminClient, JOB_TYPE_A, WORKER_NAME, 1, "Intentional failure for test");

    // Wait for failure to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_A), 1);

    // Start a 3rd process instance and cancel it to verify cancellation doesn't affect metrics
    final var instanceToCancel = startProcessInstance(adminClient, PROCESS_ID);

    // Wait for its taskA job to be visible before cancelling
    waitForJobs(adminClient, f -> f.state(JobState.CREATED).type(JOB_TYPE_A), 1);

    // Cancel the process instance (which cancels its pending taskA job)
    cancelInstance(adminClient, instanceToCancel);

    // Wait for the cancelled job to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.CANCELED).type(JOB_TYPE_A), 1);

    // Wait for metrics to be exported (3 taskA created: 1 completed, 1 failed, 1 cancelled)
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.items()).hasSize(2);
          // Ensure all 3 created jobs are reflected, with failed still at 1 (not incremented by
          // cancellation)
          assertThat(stats.items().get(0).getCreated().getCount()).isEqualTo(3L);
          assertThat(stats.items().get(0).getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnJobTypeStatisticsWithAllJobTypes(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // We have:
    // - taskA: 3 created (2 original + 1 cancelled), 1 completed, 1 failed
    // - taskB: 1 created (after completing taskA in instance 1)
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.items()).hasSize(2);

          // Results should be sorted by jobType ASC
          final var taskAStats = stats.items().get(0);
          assertThat(taskAStats.getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(taskAStats.getCreated().getCount()).isEqualTo(3L);
          assertThat(taskAStats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(taskAStats.getFailed().getCount()).isEqualTo(1L);
          assertThat(taskAStats.getWorkers()).isEqualTo(1);

          final var taskBStats = stats.items().get(1);
          assertThat(taskBStats.getJobType()).isEqualTo(JOB_TYPE_B);
          assertThat(taskBStats.getCreated().getCount()).isEqualTo(1L);
          assertThat(taskBStats.getCompleted().getCount()).isZero();
          assertThat(taskBStats.getFailed().getCount()).isZero();
          assertThat(taskBStats.getWorkers()).isEqualTo(0); // no worker activated taskB yet
        });
  }

  @Test
  void shouldNotCountCancelledJobInStatistics(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - a cancelled job should not increment the failed count
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(JOB_TYPE_A),
        stats -> {
          assertThat(stats.items()).hasSize(1);

          final var taskAStats = stats.items().get(0);
          // The cancelled job was created, so created count includes it
          assertThat(taskAStats.getCreated().getCount()).isEqualTo(3L);
          // Cancellation must NOT increment completed or failed
          assertThat(taskAStats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(taskAStats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldFilterJobTypeStatisticsByJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - filter by taskA only
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(JOB_TYPE_A),
        stats -> {
          assertThat(stats.items()).hasSize(1);

          final var taskAStats = stats.items().get(0);
          assertThat(taskAStats.getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(taskAStats.getCreated().getCount()).isEqualTo(3L);
          assertThat(taskAStats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(taskAStats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldFilterJobTypeStatisticsByJobTypeLike(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - filter by pattern "task*"
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(jt -> jt.like("task*")),
        stats -> {
          // Should match both taskA and taskB
          assertThat(stats.items()).hasSize(2);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(stats.items().get(1).getJobType()).isEqualTo(JOB_TYPE_B);
        });
  }

  @Test
  void shouldFilterJobTypeStatisticsByJobTypeIn(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - filter by taskA and taskB
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(jt -> jt.in(JOB_TYPE_A, JOB_TYPE_B)),
        stats -> {
          assertThat(stats.items()).hasSize(2);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(stats.items().get(1).getJobType()).isEqualTo(JOB_TYPE_B);
        });
  }

  @Test
  void shouldReturnEmptyForNonExistentJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - filter by non-existent job type
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType("non-existent-job-type"),
        stats -> {
          assertThat(stats.items()).isEmpty();
        });
  }

  @Test
  void shouldPaginateJobTypeStatistics(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final AtomicReference<String> endCursor = new AtomicReference<>("");
    // when - get first page with size 1
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> {},
        p -> p.limit(1),
        stats -> {
          // First page should have 1 item (taskA due to ORDER BY jobType)
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          endCursor.set(stats.page().endCursor());
        });

    // when - get second page
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> {},
        p -> p.limit(1).after(endCursor.get()),
        stats -> {
          // Second page should have 1 item (taskB)
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_B);
        });
  }

  @Test
  void shouldReturnOnlyTaskBStatistics(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - filter by taskB only
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(JOB_TYPE_B),
        stats -> {
          assertThat(stats.items()).hasSize(1);

          final var taskBStats = stats.items().get(0);
          assertThat(taskBStats.getJobType()).isEqualTo(JOB_TYPE_B);
          assertThat(taskBStats.getCreated().getCount()).isEqualTo(1L);
          assertThat(taskBStats.getCompleted().getCount()).isZero();
          assertThat(taskBStats.getFailed().getCount()).isZero();
        });
  }

  @Test
  void shouldVerifyResultsAreSortedByJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          // Results should be sorted by jobType in ascending order
          assertThat(stats.items()).hasSize(2);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(stats.items().get(1).getJobType()).isEqualTo(JOB_TYPE_B);
        });
  }
}
