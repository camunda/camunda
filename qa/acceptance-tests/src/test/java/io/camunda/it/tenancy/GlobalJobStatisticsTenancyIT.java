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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class GlobalJobStatisticsTenancyIT {

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
  }

  @Test
  void shouldReturnGlobalStatisticsForAllTenants(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // Admin has access to all tenants, so should see:
    // - CREATED: 3 taskA (2 from TENANT_A + 1 from TENANT_B) + 1 taskB (from completed taskA)
    // - COMPLETED: 1 taskA
    // - FAILED: 1 taskA
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
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
    // when/then
    // User assigned to TENANT_A should only see TENANT_A statistics:
    // - CREATED: 2 taskA + 1 taskB (from completed taskA)
    // - COMPLETED: 1 taskA
    // - FAILED: 0 (failed job is in TENANT_B)
    waitForJobStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
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
    // when/then
    // User assigned to TENANT_B should only see TENANT_B statistics:
    // - CREATED: 1 taskA
    // - COMPLETED: 0 (completed job is in TENANT_A)
    // - FAILED: 1 taskA
    waitForJobStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
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
    // when/then
    // User not assigned to any tenant should see zero statistics
    waitForJobStatistics(
        userNoTenantClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        stats -> {
          assertThat(stats.getCreated().getCount()).isZero();
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }

  @Test
  void shouldFilterStatisticsByJobType(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // when/then
    // For taskA only:
    // - CREATED: 3 (2 from TENANT_A + 1 from TENANT_B)
    // - COMPLETED: 1
    // - FAILED: 1
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
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
    // when/then
    // taskB was created in TENANT_A after completing taskA
    // User in TENANT_A should see it
    waitForJobStatistics(
        userTenantAClient,
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

  @Test
  void shouldNotSeeTaskBForUserTenantB(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // when/then
    // taskB was created in TENANT_A, so TENANT_B user should not see it
    waitForJobStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
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
    // when/then
    waitForJobStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "non-existent-job-type",
        stats -> {
          assertThat(stats.getCreated().getCount()).isZero();
          assertThat(stats.getCompleted().getCount()).isZero();
          assertThat(stats.getFailed().getCount()).isZero();
          assertThat(stats.isIncomplete()).isFalse();
        });
  }
}
