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
import static io.camunda.it.util.TestHelper.waitForAll;
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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "AWS_OS") // test is flaky on AWS OS
public class GlobalJobStatisticsIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(5);
  public static final int MAX_WORKER_NAME_LENGTH = 10;
  public static final int MAX_JOB_TYPE_LENGTH = 10;
  public static final int MAX_UNIQUE_KEYS = 5;

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthenticatedAccess()
          // set exportInterval via properties because it is not yet supported in unified config
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.exportInterval", EXPORT_INTERVAL)
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.maxWorkerNameLength",
              MAX_WORKER_NAME_LENGTH)
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.maxJobTypeLength", MAX_JOB_TYPE_LENGTH)
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.maxUniqueKeys", MAX_UNIQUE_KEYS);

  private static final String ADMIN = "admin";
  private static final String JOB_TYPE_A = "taskA";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String SHORT_WORKER_NAME = "shortWork"; // 9 chars, within limit
  private static final String LONG_WORKER_NAME = "veryLongWorkerName"; // 18 chars, exceeds limit
  private static final String LONG_JOB_TYPE = "veryLongJobType"; // 15 chars, exceeds limit of 10

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  // Time after first batch of metrics was exported (before second batch for incomplete test)
  private static OffsetDateTime firstBatchExportedTime;
  // Time after second batch of metrics was exported (for long worker name test)
  private static OffsetDateTime secondBatchExportedTime;
  // Time after third batch of metrics was exported (for long job type test)
  private static OffsetDateTime thirdBatchExportedTime;
  // Time after fourth batch of metrics was exported (for max unique keys test)
  private static OffsetDateTime fourthBatchExportedTime;

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws InterruptedException {
    // Deploy a process with service tasks (taskA, taskB, taskC)
    deployResource(adminClient, "process/service_tasks_v1.bpmn");
    // Deploy a process with a job type that EXCEEDS the limit
    final var longJobTypeProcess =
        Bpmn.createExecutableProcess("longJobTypeProcess")
            .startEvent()
            .serviceTask("longTask", t -> t.zeebeJobType(LONG_JOB_TYPE))
            .serviceTask("taskA", t -> t.zeebeJobType(JOB_TYPE_A))
            .endEvent()
            .done();
    adminClient
        .newDeployResourceCommand()
        .addProcessModel(longJobTypeProcess, "longJobTypeProcess.bpmn")
        .send()
        .join();

    // ========== FIRST BATCH: Normal metrics ==========

    // Start 2 process instances to create jobs
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);

    // Wait for the jobs to be created (2 instances x taskA = 2 taskA jobs initially)
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A), 2);

    // Complete 1 taskA job
    activateAndCompleteJobs(adminClient, JOB_TYPE_A, SHORT_WORKER_NAME, 1);

    // Wait for completion to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_A), 1);

    // Fail 1 taskA job (the remaining one from instance 2)
    activateAndFailJobs(
        adminClient, JOB_TYPE_A, SHORT_WORKER_NAME, 1, "Intentional failure for test");

    // Wait for failure to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_A), 1);

    // Wait for first batch metrics to be exported
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

    // Wait for export & store first batch export time
    firstBatchExportedTime = OffsetDateTime.now();
    Thread.sleep(2 * EXPORT_INTERVAL.toMillis());

    // ========== SECOND BATCH: Incomplete metrics (long worker name) ==========
    // Start a new process instance to have a new taskA job available
    startProcessInstance(adminClient, PROCESS_ID);

    // Wait for the new taskA job to be available
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A).state(JobState.CREATED), 1);

    // Activate and complete the job with a worker name that EXCEEDS the limit
    // This should cause the statistics to be marked as incomplete
    activateAndCompleteJobs(adminClient, JOB_TYPE_A, LONG_WORKER_NAME, 1);

    // Wait for the completion to be reflected in the job search
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).worker(LONG_WORKER_NAME), 1);

    // Wait for second batch metrics to be exported
    waitForJobStatistics(
        adminClient,
        firstBatchExportedTime,
        NOW.plusDays(1),
        stats -> {
          // taskA: 1 created, taskB: 1 created (after taskA completed)
          assertThat(stats.getCreated().getCount()).isEqualTo(2L);
          // The completed count should be 0 because the job with the long worker name
          // should not be included in the statistics
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          // The isIncomplete flag should be true because the worker name exceeded the limit
          assertThat(stats.isIncomplete()).isTrue();
        });

    // Wait for export & store second batch export time
    secondBatchExportedTime = OffsetDateTime.now();
    Thread.sleep(2 * EXPORT_INTERVAL.toMillis());

    // ========== THIRD BATCH: Incomplete metrics (long job type) ==========
    // Start a process instance with the long job type
    adminClient
        .newCreateInstanceCommand()
        .bpmnProcessId("longJobTypeProcess")
        .latestVersion()
        .send()
        .join();

    // Wait for the job to be created
    waitForJobs(adminClient, f -> f.type(LONG_JOB_TYPE).state(JobState.CREATED), 1);
    activateAndCompleteJobs(adminClient, LONG_JOB_TYPE, SHORT_WORKER_NAME, 1);
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A).state(JobState.CREATED), 1);

    // Wait for third batch metrics to be exported
    waitForJobStatistics(
        adminClient,
        secondBatchExportedTime,
        NOW.plusDays(1),
        stats -> {
          // Should have 1 valid created job (taskA), long job type not counted
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isTrue();
        });

    // Wait for export & store third batch export time
    thirdBatchExportedTime = OffsetDateTime.now();
    Thread.sleep(2 * EXPORT_INTERVAL.toMillis());

    // ========== FOURTH BATCH: Incomplete metrics (max unique keys exceeded) ==========
    // Deploy all processes first (deployments don't create job metrics)
    final var deploymentFutures =
        IntStream.rangeClosed(1, MAX_UNIQUE_KEYS + 1)
            .mapToObj(
                i -> {
                  final String uniqueJobType = "type" + i; // type1, type2, ..., type6
                  final var uniqueProcess =
                      Bpmn.createExecutableProcess("uniqueProcess" + i)
                          .startEvent()
                          .serviceTask("task" + i, t -> t.zeebeJobType(uniqueJobType))
                          .endEvent()
                          .done();
                  return adminClient
                      .newDeployResourceCommand()
                      .addProcessModel(uniqueProcess, "uniqueProcess" + i + ".bpmn")
                      .send();
                })
            .toList();
    waitForAll(deploymentFutures);

    // Now start all instances in parallel (within one export interval)
    // This ensures all jobs are created in the same batch
    final var instanceFutures =
        IntStream.rangeClosed(1, MAX_UNIQUE_KEYS + 1)
            .mapToObj(
                i ->
                    adminClient
                        .newCreateInstanceCommand()
                        .bpmnProcessId("uniqueProcess" + i)
                        .latestVersion()
                        .send())
            .toList();
    // Wait for all instances to be created
    waitForAll(instanceFutures);

    // Wait for fourth batch metrics to be exported
    waitForJobStatistics(
        adminClient,
        thirdBatchExportedTime,
        NOW.plusDays(1),
        stats -> {
          // Should be incomplete because we exceeded max unique keys
          // First MAX_UNIQUE_KEYS are tracked, the 6th is not
          assertThat(stats.getCreated().getCount()).isEqualTo(MAX_UNIQUE_KEYS);
          assertThat(stats.isIncomplete()).isTrue();
        });

    fourthBatchExportedTime = OffsetDateTime.now();
  }

  @Test
  void shouldReturnGlobalJobStatisticsWithExpectedCounts(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only first batch (before incomplete metrics)
    // We have:
    // - 2 process instances started
    // - Each process has taskA, taskB, taskC
    // - 1 taskA completed, 1 taskA failed
    // - After completing taskA in instance 1, taskB is created for instance 1
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
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
    // when/then - query only first batch (before incomplete metrics)
    // For taskA specifically: 1 completed, 1 failed, 2 created total
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
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

    // when/then - query only first batch (before incomplete metrics)
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
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
    // when/then - query only first batch (before incomplete metrics)
    // taskB: 1 created (from the completed taskA flow)
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        "taskB",
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnIncompleteWhenWorkerNameExceedsLimit(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only second batch (long worker name)
    waitForJobStatistics(
        adminClient,
        firstBatchExportedTime,
        secondBatchExportedTime,
        stats -> {
          // taskA: 1 created, taskB: 1 created (after taskA completed)
          assertThat(stats.getCreated().getCount()).isEqualTo(2L);
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isTrue();
        });
  }

  @Test
  void shouldReturnIncompleteWhenJobTypeExceedsLimit(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only third batch (long job type)
    waitForJobStatistics(
        adminClient,
        secondBatchExportedTime,
        thirdBatchExportedTime,
        stats -> {
          // Should have 1 valid created job (taskA), long job type not counted
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isTrue();
        });
  }

  @Test
  void shouldReturnIncompleteWhenMaxUniqueKeysExceeded(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only fourth batch (max unique keys exceeded)
    waitForJobStatistics(
        adminClient,
        thirdBatchExportedTime,
        fourthBatchExportedTime,
        stats -> {
          // We created MAX_UNIQUE_KEYS + 1 (6) unique job types
          // Only MAX_UNIQUE_KEYS (5) should be tracked
          assertThat(stats.getCreated().getCount()).isEqualTo(MAX_UNIQUE_KEYS);
          assertThat(stats.isIncomplete()).isTrue();
        });
  }
}
