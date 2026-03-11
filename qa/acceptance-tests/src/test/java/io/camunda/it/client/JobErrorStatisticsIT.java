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
import static io.camunda.it.util.TestHelper.waitForJobErrorStatisticsWithFilter;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.statistics.response.JobErrorStatisticsItem;
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
  private static final String WORKER_3 = "worker-3";
  private static final String WORKER_4 = "worker-4";

  private static final String ERROR_CODE_IO = "IO_ERROR";
  private static final String ERROR_MSG_IO = "Disk full";
  private static final String ERROR_CODE_TIMEOUT = "TIMEOUT";
  private static final String ERROR_MSG_TIMEOUT = "Connection timed out";
  // failJob produces entries with an empty errorCode but a non-null errorMessage
  private static final String FAIL_ERROR_MSG_1 = "Transient failure – will retry";
  private static final String FAIL_ERROR_MSG_2 = "Database unavailable";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @BeforeAll
  static void setup(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, "process/service_tasks_v1.bpmn");

    // Start 7 process instances → 7 taskA jobs:
    //   2 → throwError IO_ERROR (worker-1 and worker-2, to test distinct-worker counting)
    //   1 → throwError TIMEOUT  (worker-3)
    //   1 → failJob with FAIL_ERROR_MSG_1, retries=0  (worker-4, creates incident)
    //   1 → failJob with FAIL_ERROR_MSG_2, retries=0  (worker-4, different message)
    //   1 → failJob with FAIL_ERROR_MSG_1, retries=1  (worker-4, will be retried — same msg bucket)
    //   1 → complete successfully (must NOT appear in error statistics)
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);
    startProcessInstance(adminClient, PROCESS_ID);

    waitForJobs(adminClient, f -> f.type(JOB_TYPE_A), 7);

    // worker-1 throws IO_ERROR on 1 job
    final List<ActivatedJob> jobs1 =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(1)
            .workerName(WORKER_1)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    adminClient
        .newThrowErrorCommand(jobs1.get(0).getKey())
        .errorCode(ERROR_CODE_IO)
        .errorMessage(ERROR_MSG_IO)
        .send()
        .join();

    // worker-2 throws IO_ERROR on 1 job (same error code, different worker → workers count = 2)
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

    adminClient
        .newThrowErrorCommand(jobs2.get(0).getKey())
        .errorCode(ERROR_CODE_IO)
        .errorMessage(ERROR_MSG_IO)
        .send()
        .join();

    // worker-3 throws TIMEOUT on 1 job
    final List<ActivatedJob> jobs3 =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(1)
            .workerName(WORKER_3)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    adminClient
        .newThrowErrorCommand(jobs3.get(0).getKey())
        .errorCode(ERROR_CODE_TIMEOUT)
        .errorMessage(ERROR_MSG_TIMEOUT)
        .send()
        .join();

    // worker-4 fails 3 jobs via newFailCommand (no errorCode → null errorCode bucket)
    // Two with retries=0, one with retries=1; two different error messages
    final List<ActivatedJob> jobs4 =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(3)
            .workerName(WORKER_4)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    adminClient
        .newFailCommand(jobs4.get(0).getKey())
        .retries(0)
        .errorMessage(FAIL_ERROR_MSG_1)
        .send()
        .join();

    adminClient
        .newFailCommand(jobs4.get(1).getKey())
        .retries(0)
        .errorMessage(FAIL_ERROR_MSG_2)
        .send()
        .join();

    adminClient
        .newFailCommand(jobs4.get(2).getKey())
        .retries(1)
        .errorMessage(FAIL_ERROR_MSG_1)
        .send()
        .join();

    // worker-4 also completes 1 job successfully (COMPLETED state → must NOT appear)
    final List<ActivatedJob> jobs5 =
        adminClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE_A)
            .maxJobsToActivate(1)
            .workerName(WORKER_4)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();

    adminClient.newCompleteCommand(jobs5.get(0).getKey()).send().join();

    // Wait until at least the throwError entries are visible
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
          // 4 buckets: IO_ERROR, TIMEOUT, (null, FAIL_ERROR_MSG_1), (null, FAIL_ERROR_MSG_2)
          assertThat(stats.items()).hasSize(4);

          final var ioError =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_IO.equals(e.getErrorCode()))
                  .findFirst();
          assertThat(ioError).isPresent();
          assertThat(ioError.get().getErrorMessage()).isEqualTo(ERROR_MSG_IO);
          // IO_ERROR was thrown by both worker-1 and worker-2 → 2 distinct workers
          assertThat(ioError.get().getWorkers()).isEqualTo(2);
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
          // 4 buckets: IO_ERROR, TIMEOUT, (null, FAIL_ERROR_MSG_1), (null, FAIL_ERROR_MSG_2)
          assertThat(stats.items()).hasSize(4);

          final var ioError =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_IO.equals(e.getErrorCode()))
                  .findFirst()
                  .orElseThrow();
          // IO_ERROR was thrown by worker-1 and worker-2 → 2 distinct workers
          assertThat(ioError.getWorkers()).isEqualTo(2);

          final var timeout =
              stats.items().stream()
                  .filter(e -> ERROR_CODE_TIMEOUT.equals(e.getErrorCode()))
                  .findFirst()
                  .orElseThrow();
          // TIMEOUT was thrown by worker-3 only → 1 distinct worker
          assertThat(timeout.getWorkers()).isEqualTo(1);
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
  void shouldFilterByExactErrorCode(@Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobErrorStatisticsWithFilter(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        f -> f.errorCode(ERROR_CODE_IO),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().getFirst().getErrorCode()).isEqualTo(ERROR_CODE_IO);
          assertThat(stats.items().getFirst().getErrorMessage()).isEqualTo(ERROR_MSG_IO);
        });
  }

  @Test
  void shouldFilterByErrorMessagePattern(@Authenticated(ADMIN) final CamundaClient adminClient) {
    waitForJobErrorStatisticsWithFilter(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        f -> f.errorMessage(m -> m.like("*timed out*")),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().getFirst().getErrorCode()).isEqualTo(ERROR_CODE_TIMEOUT);
          assertThat(stats.items().getFirst().getErrorMessage()).isEqualTo(ERROR_MSG_TIMEOUT);
        });
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
    // Page through all 4 buckets one at a time and assert each item's values.
    // Composite aggregation sorts by (errorCode ASC, errorMessage ASC), empty/null first:
    //   page 1: ("",        "Database unavailable")       ← FAIL_ERROR_MSG_2
    //   page 2: ("",        "Transient failure – will retry") ← FAIL_ERROR_MSG_1
    //   page 3: ("IO_ERROR","Disk full")
    //   page 4: ("TIMEOUT", "Connection timed out")
    final var cursor1 = new AtomicReference<String>();
    final var cursor2 = new AtomicReference<String>();
    final var cursor3 = new AtomicReference<String>();
    final var cursor4 = new AtomicReference<String>();

    // page 1
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().getFirst().getErrorCode()).isNullOrEmpty();
          assertThat(stats.items().getFirst().getErrorMessage()).isEqualTo(FAIL_ERROR_MSG_2);
          assertThat(stats.page().endCursor()).isNotNull();
          cursor1.set(stats.page().endCursor());
        });

    // page 2
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1).after(cursor1.get()),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().getFirst().getErrorCode()).isNullOrEmpty();
          assertThat(stats.items().getFirst().getErrorMessage()).isEqualTo(FAIL_ERROR_MSG_1);
          assertThat(stats.page().endCursor()).isNotNull();
          cursor2.set(stats.page().endCursor());
        });

    // page 3
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1).after(cursor2.get()),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().getFirst().getErrorCode()).isEqualTo(ERROR_CODE_IO);
          assertThat(stats.items().getFirst().getErrorMessage()).isEqualTo(ERROR_MSG_IO);
          assertThat(stats.page().endCursor()).isNotNull();
          cursor3.set(stats.page().endCursor());
        });

    // page 4
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1).after(cursor3.get()),
        stats -> {
          assertThat(stats.items()).hasSize(1);
          assertThat(stats.items().getFirst().getErrorCode()).isEqualTo(ERROR_CODE_TIMEOUT);
          assertThat(stats.items().getFirst().getErrorMessage()).isEqualTo(ERROR_MSG_TIMEOUT);
          assertThat(stats.page().endCursor()).isNotNull();
          cursor4.set(stats.page().endCursor());
        });

    // page 5 — cursor exhausted, no more results
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        p -> p.limit(1).after(cursor4.get()),
        stats -> assertThat(stats.items()).isEmpty());
  }

  @Test
  void shouldCountFailedJobsWithEmptyErrorCode(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // Jobs failed via newFailCommand have no errorCode (stored as "") but do have an errorMessage.
    // They must appear in the results as separate buckets (grouped by (errorCode, errorMessage)).
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          // The two distinct fail messages produce two empty-errorCode buckets
          final var failBuckets =
              stats.items().stream()
                  .filter(e -> e.getErrorCode() == null || e.getErrorCode().isEmpty())
                  .toList();
          assertThat(failBuckets).hasSize(2);

          final var messages =
              failBuckets.stream().map(JobErrorStatisticsItem::getErrorMessage).toList();
          assertThat(messages).containsExactlyInAnyOrder(FAIL_ERROR_MSG_1, FAIL_ERROR_MSG_2);
        });
  }

  @Test
  void shouldNotCountCompletedJobsAsErrors(@Authenticated(ADMIN) final CamundaClient adminClient) {
    // Jobs that were completed successfully (COMPLETED state) must not appear in error statistics.
    waitForJobErrorStatistics(
        adminClient,
        NOW.minusDays(1),
        NOW.plusDays(1),
        JOB_TYPE_A,
        stats -> {
          // Exactly 4 buckets: IO_ERROR, TIMEOUT, and the 2 null-errorCode fail buckets.
          // The 1 completed job must not produce any additional bucket.
          assertThat(stats.items()).hasSize(4);
        });
  }
}
