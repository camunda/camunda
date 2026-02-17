/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.it.util.TestHelper.activateAndCompleteJobsForTenant;
import static io.camunda.it.util.TestHelper.activateAndFailJobsForTenant;
import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static io.camunda.it.util.TestHelper.startProcessInstanceForTenant;
import static io.camunda.it.util.TestHelper.waitForAll;
import static io.camunda.it.util.TestHelper.waitForJobStatistics;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
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
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class GlobalJobStatisticsTenancyIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(5);
  public static final int MAX_WORKER_NAME_LENGTH = 10;
  public static final int MAX_TENANT_ID_LENGTH = 10;
  public static final int MAX_UNIQUE_KEYS = 5;

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess()
          // set exportInterval via properties because it is not yet supported in unified config
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.exportInterval", EXPORT_INTERVAL)
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.maxWorkerNameLength",
              MAX_WORKER_NAME_LENGTH)
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.maxTenantIdLength", MAX_TENANT_ID_LENGTH)
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.maxUniqueKeys", MAX_UNIQUE_KEYS);

  private static final String ADMIN = "admin";
  private static final String USER_TENANT_A = "userTenantA";
  private static final String USER_TENANT_B = "userTenantB";
  private static final String USER_NO_TENANT = "userNoTenant";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String JOB_TYPE_A = "taskA";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String SHORT_WORKER_NAME = "shortWork"; // 9 chars, within limit
  private static final String LONG_WORKER_NAME = "veryLongWorkerName"; // 18 chars, exceeds limit
  private static final String LONG_TENANT_ID = "veryLongTenantId"; // 16 chars, exceeds limit of 10

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER_TENANT_A_USER =
      new TestUser(USER_TENANT_A, "password", List.of());

  @UserDefinition
  private static final TestUser USER_TENANT_B_USER =
      new TestUser(USER_TENANT_B, "password", List.of());

  @UserDefinition
  private static final TestUser USER_NO_TENANT_USER =
      new TestUser(USER_NO_TENANT, "password", List.of());

  // Time after first batch of metrics was exported (before second batch for incomplete test)
  private static OffsetDateTime firstBatchExportedTime;
  // Time after second batch of metrics was exported (for long worker name test)
  private static OffsetDateTime secondBatchExportedTime;
  // Time after third batch of metrics was exported (for long tenant id test)
  private static OffsetDateTime thirdBatchExportedTime;
  // Time after fourth batch of metrics was exported (for max unique keys test)
  private static OffsetDateTime fourthBatchExportedTime;

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws InterruptedException {
    // ========== FIRST BATCH: Normal metrics ==========
    // Create tenants and assign users
    createTenant(adminClient, TENANT_A, TENANT_A, ADMIN, USER_TENANT_A);
    createTenant(adminClient, TENANT_B, TENANT_B, ADMIN, USER_TENANT_B);
    // USER_NO_TENANT is not assigned to any tenant

    // Deploy processes for each tenant
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);

    // Start process instances for each tenant to create jobs
    // TENANT_A: 2 instances -> 2 taskA jobs created
    // TENANT_B: 1 instance -> 1 taskA job created
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_B);

    // Wait for jobs to be available
    waitForJobs(adminClient, f -> f.tenantId(TENANT_A), 2);
    waitForJobs(adminClient, f -> f.tenantId(TENANT_B), 1);

    // Complete 1 taskA job from TENANT_A
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_A, TENANT_A, SHORT_WORKER_NAME, 1);

    // Wait for completion to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED), 1);

    // Fail 1 taskA job from TENANT_B
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_A, TENANT_B, SHORT_WORKER_NAME, 1, "Intentional failure for test");

    // Wait for failure to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.FAILED), 1);

    // Wait for first batch metrics to be exported
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          // 3 taskA (2 TENANT_A + 1 TENANT_B) + 1 taskB (created after completing taskA)
          assertThat(stats.getCreated().getCount()).isEqualTo(4L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });

    // Wait for export & store first batch export time
    firstBatchExportedTime = OffsetDateTime.now();
    Thread.sleep(2 * EXPORT_INTERVAL.toMillis());

    // ========== SECOND BATCH: Incomplete metrics (long worker name) ==========
    // Start a new process instance in TENANT_A to have a new taskA job available
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);

    // Wait for the new taskA job to be available
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A).state(JobState.CREATED).tenantId(TENANT_A), 2);

    // Activate and complete the job with a worker name that EXCEEDS the limit
    // This should cause the statistics to be marked as incomplete
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_A, TENANT_A, LONG_WORKER_NAME, 1);

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

    // ========== THIRD BATCH: Incomplete metrics (long tenant id) ==========
    // Create a tenant with a long ID that EXCEEDS the limit
    createTenant(adminClient, LONG_TENANT_ID, LONG_TENANT_ID, ADMIN);

    // Deploy a process to the long tenant
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", LONG_TENANT_ID);

    // Start a process instance in the long tenant
    startProcessInstanceForTenant(adminClient, PROCESS_ID, LONG_TENANT_ID);
    // Create a valid job in TENANT_A to ensure at least one metric is recorded
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);

    // Wait for the new taskA job to be available
    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A).tenantId(LONG_TENANT_ID), 1);

    // Wait for third batch metrics to be exported
    waitForJobStatistics(
        adminClient,
        secondBatchExportedTime,
        NOW.plusDays(1),
        stats -> {
          // Should have 1 valid created job from TENANT_A, long tenant ID job not counted
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isTrue();
        });

    // Wait for export & store third batch export time
    thirdBatchExportedTime = OffsetDateTime.now();
    Thread.sleep(2 * EXPORT_INTERVAL.toMillis());

    // ========== FOURTH BATCH: Incomplete metrics (max unique keys exceeded) ==========
    // Create unique tenant+jobType combinations to exceed MAX_UNIQUE_KEYS (5)
    // Note: The first MAX_UNIQUE_KEYS will be tracked, ensuring at least valid data is recorded
    // Deploy all processes first (deployments don't create job metrics)
    final var deploymentFutures =
        IntStream.rangeClosed(1, MAX_UNIQUE_KEYS + 1)
            .mapToObj(
                i -> {
                  final String uniqueJobType = "type" + i; // type1, type2, ..., type6
                  final var uniqueProcess =
                      Bpmn.createExecutableProcess("uniqueProc" + i)
                          .startEvent()
                          .serviceTask("task" + i, t -> t.zeebeJobType(uniqueJobType))
                          .endEvent()
                          .done();
                  return adminClient
                      .newDeployResourceCommand()
                      .addProcessModel(uniqueProcess, "uniqueProc" + i + ".bpmn")
                      .tenantId(TENANT_A)
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
                        .bpmnProcessId("uniqueProc" + i)
                        .latestVersion()
                        .tenantId(TENANT_A)
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
  void shouldReturnGlobalStatisticsForAllTenants(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only first batch (before incomplete metrics)
    // Admin has access to all tenants, so should see:
    // - CREATED: 3 taskA (2 from TENANT_A + 1 from TENANT_B) + 1 taskB (from completed taskA)
    // - COMPLETED: 1 taskA
    // - FAILED: 1 taskA
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(4L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnOnlyTenantAStatisticsForUserTenantA(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // when/then - query only first batch (before incomplete metrics)
    // User assigned to TENANT_A should only see TENANT_A statistics:
    // - CREATED: 2 taskA + 1 taskB (from completed taskA)
    // - COMPLETED: 1 taskA
    // - FAILED: 0 (failed job is in TENANT_B)
    waitForJobStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(2L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnOnlyTenantBStatisticsForUserTenantB(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // when/then - query only first batch (before incomplete metrics)
    // User assigned to TENANT_B should only see TENANT_B statistics:
    // - CREATED: 1 taskA
    // - COMPLETED: 0 (completed job is in TENANT_A)
    // - FAILED: 1 taskA
    waitForJobStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnZeroStatisticsForUserWithNoTenantAccess(
      @Authenticated(USER_NO_TENANT) final CamundaClient userNoTenantClient) {
    // when/then - query only first batch (before incomplete metrics)
    // User not assigned to any tenant should see zero statistics
    waitForJobStatistics(
        userNoTenantClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        stats -> {
          assertThat(stats.getCreated().getCount()).isZero();
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldFilterStatisticsByJobType(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only first batch (before incomplete metrics)
    // For taskA only:
    // - CREATED: 3 (2 from TENANT_A + 1 from TENANT_B)
    // - COMPLETED: 1
    // - FAILED: 1
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.getCreated().getCount()).isEqualTo(3L);
          assertThat(stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(stats.getFailed().getCount()).isEqualTo(1L);
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnTaskBStatisticsOnlyForTenantA(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // when/then - query only first batch (before incomplete metrics)
    // taskB was created in TENANT_A after completing taskA
    // User in TENANT_A should see it
    waitForJobStatistics(
        userTenantAClient,
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
  void shouldNotSeeTaskBForUserTenantB(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // when/then - query only first batch (before incomplete metrics)
    // taskB was created in TENANT_A, so TENANT_B user should not see it
    waitForJobStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        "taskB",
        stats -> {
          assertThat(stats.getCreated().getCount()).isZero();
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnZeroForNonExistentJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only first batch (before incomplete metrics)
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        firstBatchExportedTime,
        "non-existent-job-type",
        stats -> {
          assertThat(stats.getCreated().getCount()).isZero();
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldReturnIncompleteWhenWorkerNameExceedsLimit(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only second batch (after first batch, containing incomplete metrics)
    // The statistics should be marked as incomplete because we used a worker name
    // that exceeds the configured limit
    waitForJobStatistics(
        adminClient,
        firstBatchExportedTime,
        secondBatchExportedTime,
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
  }

  @Test
  void shouldReturnIncompleteWhenTenantIdExceedsLimit(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - query only third batch (after second batch, containing incomplete metrics)
    // The statistics should be marked as incomplete because we used a tenant ID
    // that exceeds the configured limit
    waitForJobStatistics(
        adminClient,
        secondBatchExportedTime,
        thirdBatchExportedTime,
        stats -> {
          // Should have 1 valid created job from TENANT_A, long tenant ID job not counted
          assertThat(stats.getCreated().getCount()).isEqualTo(1L);
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          // The isIncomplete flag should be true because the tenant ID exceeded the limit
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
          // We created MAX_UNIQUE_KEYS + 1 (6) unique tenant+jobType combinations
          // Only MAX_UNIQUE_KEYS (5) should be tracked
          assertThat(stats.getCreated().getCount()).isEqualTo(MAX_UNIQUE_KEYS);
          assertThat(stats.isIncomplete()).isTrue();
        });
  }
}
