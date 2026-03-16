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
import static io.camunda.it.util.TestHelper.waitForJobStatistics;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest(envVars = "ZEEBE_BROKER_EXPERIMENTAL_ENGINE_JOBMETRICS_EXPORTINTERVAL=PT2S")
public class GlobalJobStatisticsIT {

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

    // Wait for metrics to be exported
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          // 2 taskA + 1 taskB (created after completing taskA in instance 1)
          assertThat(stats.getCreated().getCount()).isEqualTo(3L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnGlobalJobStatisticsWithExpectedCounts(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // We have:
    // - 2 process instances started
    // - Each process has taskA, taskB, taskC
    // - 1 taskA completed, 1 taskA failed
    // - After completing taskA in instance 1, taskB is created for instance 1
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(3L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnStatisticsFilteredByJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // For taskA specifically: 1 completed, 1 failed, 2 created total
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          // taskA: 2 created, 1 completed, 1 failed
          assertThat(stats.getCreated().getCount()).isEqualTo(2L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnZeroCountsForNonExistentJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final String nonExistentJobType = "non-existent-job-type-xyz";

    // when/then
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        nonExistentJobType,
        stats -> {
          assertThat(stats.getCreated().getCount()).isZero();
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnOnlyCreatedJobsForTaskB(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // taskB: 1 created (from the completed taskA flow)
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "taskB",
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }
}
