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
import static io.camunda.it.util.TestHelper.waitForJobWorkerStatistics;
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
public class JobWorkerStatisticsTenancyIT {

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
  // TENANT_A uses two workers; TENANT_B uses one worker
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
    // Create tenants and assign users
    createTenant(adminClient, TENANT_A, TENANT_A, ADMIN, USER_TENANT_A);
    createTenant(adminClient, TENANT_B, TENANT_B, ADMIN, USER_TENANT_B);

    // Deploy processes for each tenant
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);

    // TENANT_A: 3 instances -> 3 taskA jobs; TENANT_B: 1 instance -> 1 taskA job
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_B);

    // Wait for jobs
    waitForJobs(adminClient, f -> f.tenantId(TENANT_A).type(JOB_TYPE_A), 3);
    waitForJobs(adminClient, f -> f.tenantId(TENANT_B).type(JOB_TYPE_A), 1);

    // TENANT_A: worker-1 completes 2 taskA jobs -> creates 2 taskB jobs
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_A, TENANT_A, WORKER_1, 2);
    waitForJobs(adminClient, f -> f.state(JobState.COMPLETED).tenantId(TENANT_A), 2);

    // TENANT_A: worker-2 fails 1 taskA job
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_A, TENANT_A, WORKER_2, 1, "Intentional failure");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).tenantId(TENANT_A), 1);

    // TENANT_B: worker-1 fails 1 taskA job (no taskB created in TENANT_B)
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_A, TENANT_B, WORKER_1, 1, "Intentional failure");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).tenantId(TENANT_B), 1);

    // Wait for 2 taskB jobs in TENANT_A (created after the 2 taskA completions)
    waitForJobs(adminClient, f -> f.tenantId(TENANT_A).type(JOB_TYPE_B).state(JobState.CREATED), 2);

    // TENANT_A: worker-1 completes 1 taskB job
    activateAndCompleteJobsForTenant(adminClient, JOB_TYPE_B, TENANT_A, WORKER_1, 1);
    waitForJobs(
        adminClient, f -> f.state(JobState.COMPLETED).type(JOB_TYPE_B).tenantId(TENANT_A), 1);

    // TENANT_A: worker-3 fails 1 taskB job
    activateAndFailJobsForTenant(
        adminClient, JOB_TYPE_B, TENANT_A, WORKER_3, 1, "Intentional failure");
    waitForJobs(adminClient, f -> f.state(JobState.FAILED).type(JOB_TYPE_B).tenantId(TENANT_A), 1);

    // Wait for metrics export
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
  void shouldReturnAllWorkersForAllTenants(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // Admin (taskA): worker-1 completed 2 (TENANT_A), worker-2 failed 1 (TENANT_A),
    //                worker-1 failed 1 (TENANT_B) => worker-1: 2 completed + 1 failed, worker-2: 1
    // failed
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSize(2);

          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(2L);
          assertThat(worker1Stats.getFailed().getCount()).isEqualTo(1L);

          final var worker2Stats = stats.items().get(1);
          assertThat(worker2Stats.getWorker()).isEqualTo(WORKER_2);
          assertThat(worker2Stats.getCompleted().getCount()).isZero();
          assertThat(worker2Stats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnAllWorkersForTaskBForAdmin(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // Admin (taskB, TENANT_A only): worker-1 completed 1, worker-3 failed 1
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> {
          assertThat(stats.items()).hasSize(2);

          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(worker1Stats.getFailed().getCount()).isZero();

          final var worker3Stats = stats.items().get(1);
          assertThat(worker3Stats.getWorker()).isEqualTo(WORKER_3);
          assertThat(worker3Stats.getCompleted().getCount()).isZero();
          assertThat(worker3Stats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnOnlyTenantAWorkersForTaskAForTenantAUser(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // TENANT_A (taskA): worker-1 completed 2, worker-2 failed 1 — no TENANT_B data
    waitForJobWorkerStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSize(2);

          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(2L);
          assertThat(worker1Stats.getFailed().getCount()).isZero();

          final var worker2Stats = stats.items().get(1);
          assertThat(worker2Stats.getWorker()).isEqualTo(WORKER_2);
          assertThat(worker2Stats.getCompleted().getCount()).isZero();
          assertThat(worker2Stats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnOnlyTenantAWorkersForTaskBForTenantAUser(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // TENANT_A (taskB): worker-1 completed 1, worker-3 failed 1
    waitForJobWorkerStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> {
          assertThat(stats.items()).hasSize(2);

          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isEqualTo(1L);
          assertThat(worker1Stats.getFailed().getCount()).isZero();

          final var worker3Stats = stats.items().get(1);
          assertThat(worker3Stats.getWorker()).isEqualTo(WORKER_3);
          assertThat(worker3Stats.getCompleted().getCount()).isZero();
          assertThat(worker3Stats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnOnlyTenantBWorkersForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // TENANT_B (taskA): only worker-1 failed 1 job
    waitForJobWorkerStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSize(1);

          final var worker1Stats = stats.items().get(0);
          assertThat(worker1Stats.getWorker()).isEqualTo(WORKER_1);
          assertThat(worker1Stats.getCompleted().getCount()).isZero();
          assertThat(worker1Stats.getFailed().getCount()).isEqualTo(1L);
        });
  }

  @Test
  void shouldReturnEmptyTaskBForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // TENANT_B has no taskB activity — no taskA was completed in TENANT_B
    waitForJobWorkerStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_B,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldNotSeeWorker2OrWorker3ForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // worker-2 and worker-3 only processed TENANT_A jobs
    waitForJobWorkerStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).extracting("worker").doesNotContain(WORKER_2, WORKER_3));
  }

  @Test
  void shouldReturnEmptyForUserWithNoTenantAccess(
      @Authenticated(USER_NO_TENANT) final CamundaClient userNoTenantClient) {
    waitForJobWorkerStatistics(
        userNoTenantClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldPaginateTaskAWorkerStatistics(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page: worker-1
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
  void shouldPaginateTaskBWorkerStatistics(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page: worker-1
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
  void shouldReturnEmptyForNonExistentJobTypeAcrossTenants(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobWorkerStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "non-existent-job-type",
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
