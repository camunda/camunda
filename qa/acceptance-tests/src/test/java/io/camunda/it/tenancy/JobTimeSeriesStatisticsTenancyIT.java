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
import static io.camunda.it.util.TestHelper.waitForJobTimeSeriesStatistics;
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

@MultiDbTest
public class JobTimeSeriesStatisticsTenancyIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final Duration EXPORT_INTERVAL = Duration.ofSeconds(2);

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess()
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
  private static final String WORKER_1 = "worker-1";
  private static final String WORKER_2 = "worker-2";
  private static final String WORKER_3 = "worker-3";

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
    createTenant(adminClient, TENANT_A, TENANT_A, ADMIN, USER_TENANT_A);
    createTenant(adminClient, TENANT_B, TENANT_B, ADMIN, USER_TENANT_B);

    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);

    // TENANT_A: 3 instances → 3 taskA jobs; TENANT_B: 1 instance → 1 taskA job
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_B);

    waitForJobs(adminClient, f -> f.tenantId(TENANT_A).type(JOB_TYPE_A), 3);
    waitForJobs(adminClient, f -> f.tenantId(TENANT_B).type(JOB_TYPE_A), 1);

    // TENANT_A: worker-1 completes 2 taskA → creates 2 taskB jobs
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_A, TENANT_A, WORKER_1, 2);
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).tenantId(TENANT_A), 2);

    // TENANT_A: worker-2 fails 1 taskA
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_A, TENANT_A, WORKER_2, 1, "Intentional failure");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).tenantId(TENANT_A), 1);

    // TENANT_B: worker-1 fails 1 taskA
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_A, TENANT_B, WORKER_1, 1, "Intentional failure");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).tenantId(TENANT_B), 1);

    // Wait for 2 taskB jobs in TENANT_A
    waitForJobs(adminClient, f -> f.tenantId(TENANT_A).type(JOB_TYPE_B).state(JobState.CREATED), 2);

    // TENANT_A: worker-1 completes 1 taskB; worker-3 fails 1 taskB
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_B, TENANT_A, WORKER_1, 1);
    waitForJobs(
        adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_B).tenantId(TENANT_A), 1);
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_B, TENANT_A, WORKER_3, 1, "Intentional failure");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_B).tenantId(TENANT_A), 1);

    // Wait for metrics export
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
  void shouldReturnAllTenantsDataForAdmin(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // Admin sees all tenants combined: TENANT_A (2 completed, 1 failed) + TENANT_B (0 completed, 1
    // failed) = 2 completed, 2 failed
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isEqualTo(2L);
          assertThat(totalFailed).isEqualTo(2L);
        });
  }

  @Test
  void shouldReturnOnlyTenantADataForTenantAUser(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // TENANT_A (taskA): worker-1 completed 2, worker-2 failed 1
    waitForJobTimeSeriesStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          // Only TENANT_A: 2 completed, 1 failed (not TENANT_B's 0 completed, 1 failed)
          assertThat(totalCompleted).isEqualTo(2L);
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnOnlyTenantBDataForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // TENANT_B (taskA): worker-1 failed 1 job only
    waitForJobTimeSeriesStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          final long totalCompleted =
              stats.items().stream().mapToLong(b -> b.getCompleted().getCount()).sum();
          final long totalFailed =
              stats.items().stream().mapToLong(b -> b.getFailed().getCount()).sum();
          assertThat(totalCompleted).isZero();
          assertThat(totalFailed).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnEmptyTaskBForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // TENANT_B has no taskB activity
    waitForJobTimeSeriesStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldReturnEmptyForUserWithNoTenantAccess(
      @Authenticated(USER_NO_TENANT) final CamundaClient userNoTenantClient) {
    waitForJobTimeSeriesStatistics(
        userNoTenantClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldReturnTaskBDataForTenantAUser(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // TENANT_A (taskB): worker-1 completed 1, worker-3 failed 1
    waitForJobTimeSeriesStatistics(
        userTenantAClient,
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
  void shouldPaginateTimeSeriesForAdmin(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page: 1 bucket
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
  void shouldReturnEmptyForNonExistentJobTypeAcrossTenants(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobTimeSeriesStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "non-existent-job-type",
        stats -> assertThat(stats.items()).isEmpty());
  }
}
