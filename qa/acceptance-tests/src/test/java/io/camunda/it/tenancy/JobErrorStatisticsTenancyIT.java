/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static io.camunda.it.util.TestHelper.startProcessInstanceForTenant;
import static io.camunda.it.util.TestHelper.waitForJobErrorStatistics;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
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
public class JobErrorStatisticsTenancyIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String ADMIN = "admin";
  private static final String USER_TENANT_A = "userTenantA";
  private static final String USER_TENANT_B = "userTenantB";
  private static final String USER_NO_TENANT = "userNoTenant";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String JOB_TYPE_A = "taskA";
  private static final String PROCESS_ID = "service_tasks_v1";
  private static final String WORKER_1 = "worker-1";
  private static final String WORKER_2 = "worker-2";

  private static final String ERROR_CODE_IO = "IO_ERROR";
  private static final String ERROR_MSG_IO = "Disk full";
  private static final String ERROR_CODE_TIMEOUT = "TIMEOUT";
  private static final String ERROR_MSG_TIMEOUT = "Connection timed out";

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
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A, TENANT_A, ADMIN, USER_TENANT_A);
    createTenant(adminClient, TENANT_B, TENANT_B, ADMIN, USER_TENANT_B);

    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResourceForTenant(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);

    // TENANT_A: 2 instances → 2 taskA jobs
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_A);

    // TENANT_B: 1 instance → 1 taskA job
    startProcessInstanceForTenant(adminClient, PROCESS_ID, TENANT_B);

    waitForJobs(adminClient, f -> f.tenantId(TENANT_A).type(JOB_TYPE_A), 2);
    waitForJobs(adminClient, f -> f.tenantId(TENANT_B).type(JOB_TYPE_A), 1);

    // TENANT_A: worker-1 throws IO_ERROR on 2 jobs
    final List<ActivatedJob> tenantAJobs =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(2)
            .workerName(WORKER_1)
            .tenantIds(TENANT_A)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    for (final ActivatedJob job : tenantAJobs) {
      adminClient
          .newThrowErrorCommand(job.getKey())
          .errorCode(ERROR_CODE_IO)
          .errorMessage(ERROR_MSG_IO)
          .send()
          .join();
    }

    // TENANT_B: worker-2 throws TIMEOUT on 1 job
    final List<ActivatedJob> tenantBJobs =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(1)
            .workerName(WORKER_2)
            .tenantIds(TENANT_B)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    for (final ActivatedJob job : tenantBJobs) {
      adminClient
          .newThrowErrorCommand(job.getKey())
          .errorCode(ERROR_CODE_TIMEOUT)
          .errorMessage(ERROR_MSG_TIMEOUT)
          .send()
          .join();
    }

    // Wait until admin can see data from both tenants
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).hasSizeGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldReturnAllTenantsDataForAdmin(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // Admin sees both TENANT_A (IO_ERROR) and TENANT_B (TIMEOUT)
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSizeGreaterThanOrEqualTo(2);

          final var ioError =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_IO.equals(e.getErrorCode()))
                  .findFirst();
          assertThat(ioError).isPresent();

          final var timeout =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_TIMEOUT.equals(e.getErrorCode()))
                  .findFirst();
          assertThat(timeout).isPresent();
        });
  }

  @Test
  void shouldReturnOnlyTenantADataForTenantAUser(
      @Authenticated(USER_TENANT_A) final CamundaClient userTenantAClient) {
    // TENANT_A user only sees IO_ERROR, not TIMEOUT (which belongs to TENANT_B)
    waitForJobErrorStatistics(
        userTenantAClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          assertThat(stats.items().stream().allMatch(e -> ERROR_CODE_IO.equals(e.getErrorCode())))
              .isTrue();
        });
  }

  @Test
  void shouldReturnOnlyTenantBDataForTenantBUser(
      @Authenticated(USER_TENANT_B) final CamundaClient userTenantBClient) {
    // TENANT_B user only sees TIMEOUT, not IO_ERROR (which belongs to TENANT_A)
    waitForJobErrorStatistics(
        userTenantBClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();
          assertThat(
                  stats.items().stream().allMatch(e -> ERROR_CODE_TIMEOUT.equals(e.getErrorCode())))
              .isTrue();
        });
  }

  @Test
  void shouldReturnEmptyForUserWithNoTenantAccess(
      @Authenticated(USER_NO_TENANT) final CamundaClient userNoTenantClient) {
    waitForJobErrorStatistics(
        userNoTenantClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isEmpty());
  }
}
