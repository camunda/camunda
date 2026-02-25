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
import static io.camunda.it.util.TestHelper.waitForJobTypeStatistics;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class JobTypeStatisticsTenancyIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess()
          // set exportInterval via properties because it is not yet supported in unified config
          .withProperty(
              "zeebe.broker.experimental.engine.jobMetrics.exportInterval", EXPORT_INTERVAL);

  private static final String ADMIN = "admin";
  private static final String USER_TENANT_A = "userTenantA";
  private static final String USER_TENANT_B = "userTenantB";
  private static final String USER_NO_TENANT = "userNoTenant";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String JOB_TYPE_A = "taskA";
  private static final String JOB_TYPE_B = "taskB";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String WORKER_NAME = "testWorker";

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

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient)
      throws InterruptedException {
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
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_A, TENANT_A, WORKER_NAME, 1);

    // Wait for completion to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED), 1);

    // Fail 1 taskA job from TENANT_B
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_A, TENANT_B, WORKER_NAME, 1, "Intentional failure for test");

    // Wait for failure to be reflected
    waitForJobs(adminClient, f -> f.state(JobState.FAILED), 1);

    // Wait for metrics to be exported
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          // Admin should see taskA from both tenants + taskB from TENANT_A
          assertThat(stats.items()).hasSizeGreaterThanOrEqualTo(2);
        });
  }

  @Test
  void shouldReturnJobTypeStatisticsForAllTenants(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // Admin has access to all tenants, so should see:
    // - taskA: 3 created (2 TENANT_A + 1 TENANT_B), 1 completed, 1 failed
    // - taskB: 1 created (from TENANT_A after completing taskA)
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.items()).hasSize(2);

          // Results sorted by jobType ASC
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
        });
  }

  @Test
  void shouldReturnOnlyTenantAJobTypeStatistics(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // when/then
    // User assigned to TENANT_A should only see TENANT_A statistics:
    // - taskA: 2 created, 1 completed, 0 failed
    // - taskB: 1 created
    waitForJobTypeStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.items()).hasSize(2);

          final var taskAStats = stats.items().get(0);
          assertThat(taskAStats.getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(taskAStats.getCreated().getCount()).isEqualTo(2L);
          assertThat(taskAStats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(taskAStats.getFailed().getCount()).isZero();

          final var taskBStats = stats.items().get(1);
          assertThat(taskBStats.getJobType()).isEqualTo(JOB_TYPE_B);
          assertThat(taskBStats.getCreated().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnOnlyTenantBJobTypeStatistics(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // when/then
    // User assigned to TENANT_B should only see TENANT_B statistics:
    // - taskA: 1 created, 0 completed, 1 failed
    waitForJobTypeStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.items()).hasSize(1);

          final var taskAStats = stats.items().get(0);
          assertThat(taskAStats.getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(taskAStats.getCreated().getCount()).isEqualTo(1L);
          assertThat(taskAStats.getCompleted().getCount()).isZero();
          assertThat(taskAStats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnEmptyForUserWithNoTenantAccess(
      @Authenticated(USER_NO_TENANT) final CamundaClient userNoTenantClient) {
    // when/then
    // User not assigned to any tenant should see no statistics
    waitForJobTypeStatistics(
        userNoTenantClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.items()).isEmpty();
        });
  }

  @Test
  void shouldFilterByJobTypeForTenantAUser(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // when/then - filter by taskA only
    waitForJobTypeStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(JOB_TYPE_A),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(stats.items().get(0).getCreated().getCount()).isEqualTo(2L);
        });
  }

  @Test
  void shouldFilterByJobTypeForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // when/then - filter by taskA only
    waitForJobTypeStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(JOB_TYPE_A),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(stats.items().get(0).getCreated().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldNotSeeTenantATaskBForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // when/then
    // taskB was created in TENANT_A, so TENANT_B user should not see it
    waitForJobTypeStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(JOB_TYPE_B),
        stats -> {
          assertThat(stats.items()).isEmpty();
        });
  }

  @Test
  void shouldPaginateJobTypeStatisticsWithTenantFiltering(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");
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
  void shouldFilterByJobTypeLikePatternWithTenancy(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then - filter by pattern "task*"
    waitForJobTypeStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        f -> f.jobType(jt -> jt.like("task*")),
        stats -> {
          // Should match both taskA and taskB across all tenants
          assertThat(stats.items()).hasSize(2);
          assertThat(stats.items().get(0).getJobType()).isEqualTo(JOB_TYPE_A);
          assertThat(stats.items().get(1).getJobType()).isEqualTo(JOB_TYPE_B);
        });
  }

  @Test
  void shouldReturnEmptyForNonExistentJobTypeWithTenancy(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
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
  void shouldVerifyResultsAreSortedByJobTypeAcrossTenants(
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
