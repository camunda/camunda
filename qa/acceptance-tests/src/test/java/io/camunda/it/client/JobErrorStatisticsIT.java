/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobErrorStatistics;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
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
@CompatibilityTest
public class JobErrorStatisticsIT {

  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthenticatedAccess();

  private static final String ADMIN = "admin";
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

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, "process/service_tasks_v1.bpmn");

    // Start 3 process instances → 3 taskA jobs
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);

    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A), 3);

    // worker-1 throws IO_ERROR on 2 jobs
    final List<ActivatedJob> jobs1 =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(2)
            .workerName(WORKER_1)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    for (final ActivatedJob job : jobs1) {
      adminClient
          .newThrowErrorCommand(job.getKey())
          .errorCode(ERROR_CODE_IO)
          .errorMessage(ERROR_MSG_IO)
          .send()
          .join();
    }

    // worker-2 throws TIMEOUT on 1 job
    final List<ActivatedJob> jobs2 =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(1)
            .workerName(WORKER_2)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    for (final ActivatedJob job : jobs2) {
      adminClient
          .newThrowErrorCommand(job.getKey())
          .errorCode(ERROR_CODE_TIMEOUT)
          .errorMessage(ERROR_MSG_TIMEOUT)
          .send()
          .join();
    }

    // Wait for error stats to be available
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isNotEmpty());
  }

  @Test
  void shouldReturnErrorStatisticsGroupedByErrorCode(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).isNotEmpty();

          final var ioError =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_IO.equals(e.getErrorCode()))
                  .findFirst();
          assertThat(ioError).isPresent();
          assertThat(ioError.get().getErrorMessage()).isEqualTo(ERROR_MSG_IO);
          assertThat(ioError.get().getWorkers()).isEqualTo(1); // only worker-1
        });
  }

  @Test
  void shouldCountDistinctWorkers(@Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          assertThat(stats.items()).hasSize(2); // IO_ERROR and TIMEOUT

          final var ioError =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_IO.equals(e.getErrorCode()))
                  .findFirst()
                  .orElseThrow();
          // Both IO_ERROR jobs came from worker-1 → distinct worker count is 1
          assertThat(ioError.getWorkers()).isEqualTo(1);

          final var timeout =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_TIMEOUT.equals(e.getErrorCode()))
                  .findFirst()
                  .orElseThrow();
          assertThat(timeout.getWorkers()).isEqualTo(1); // worker-2
        });
  }

  @Test
  void shouldReturnEmptyForNonExistentJobType(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        "non-existent-job-type",
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenTimeRangeExcludesAllData(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(30),
        NOW.minusDays(29),
        JOB_TYPE_A,
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldSupportPagination(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final var endCursor = new AtomicReference<>("");

    // First page of size 1 — should return exactly one error bucket
    waitForJobErrorStatistics(
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

    // Second page using the cursor — should return the remaining error bucket
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1).after(endCursor.get()),
        stats -> assertThat(stats.items()).hasSize(1));
  }
}
