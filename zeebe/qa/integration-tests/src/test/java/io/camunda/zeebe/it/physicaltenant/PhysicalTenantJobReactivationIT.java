/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Verifies that jobs the gateway cannot fit into an activate-jobs response are reactivated on the
 * physical tenant they were activated from. When a gRPC {@code ActivateJobsResponse} would exceed
 * {@code maxMessageSize}, {@code RoundRobinActivateJobsHandler} defers the jobs that do not fit and
 * immediately FAILs them via a gateway-internal {@code BrokerFailJobRequest}, so they become
 * activatable again right away instead of staying locked until their activation timeout.
 *
 * <p>Before <a href="https://github.com/camunda/camunda/issues/60103">#60103</a> that internal
 * request carried no partition group and was silently routed to the <em>default</em> physical
 * tenant: the FAIL either addressed a partition id the default group does not have at all, or was
 * rejected there because the job key is unknown to the default engine. Either way the deferred jobs
 * stayed invisibly locked on their own tenant. Only an asymmetric multi-physical-tenant cluster
 * observes this: on a single-tenant cluster the default group <em>is</em> the right target, and on
 * a symmetric cluster a misrouted request often finds an identical-looking partition, so the
 * misroute at most surfaces as a quiet rejection. Here the default tenant runs 1 partition while
 * {@value #TENANT_A} runs {@value #TENANT_A_PARTITIONS}, so a misrouted FAIL cannot even resolve an
 * address for most of the jobs, and the deferred jobs never come back within the await window.
 */
@ZeebeIntegration
final class PhysicalTenantJobReactivationIT {

  private static final String TENANT_A = "tenanta";
  private static final int TENANT_A_PARTITIONS = 3;

  // Each variable value is a string of literal `"` characters. The broker stores variables as
  // MsgPack (~7 KB per JobRecord) but the gRPC gateway re-encodes them as JSON in the
  // ActivateJobsResponse proto, escaping each `"` to `\"` and doubling the byte count (~14 KB per
  // ActivatedJob). With maxMessageSize = 64 KB and 15 jobs round-robined over tenanta's 3
  // partitions (~5 per partition; by pigeonhole at least one partition holds >= 5):
  //   - the broker fits a partition's ~5 jobs into a single JobBatchRecord
  //     (5 * 7.5 KB + 8 KB safety buffer <= 64 KB)
  //   - but that partition's gateway response (5 * ~14 KB) cannot fit within 64 KB, so at least
  //     one job is deferred and FAILed through RoundRobinActivateJobsHandler.toFailJobRequest.
  // The gRPC path is required: the REST gateway's defer check compares against the broker record
  // size, not the REST response size, so it cannot trigger this path under unified config that
  // ties broker and gateway maxMessageSize together.
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(64);
  private static final int QUOTE_PAYLOAD_LENGTH = 7000;
  private static final int JOBS_TO_CREATE = 15;

  // long enough that a job activated during this test can never hit its activation timeout: a job
  // that becomes activatable again within the await windows below can only have been FAILed back
  // by the gateway, not released by a timeout
  private static final Duration JOB_TIMEOUT = Duration.ofMinutes(10);

  private static final String JOB_TYPE = "reactivation-task";
  private static final String PROCESS_ID = "reactivation-process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .done();

  // the default tenant and tenant A both run broker-only (no secondary storage); the deliberately
  // different partition counts are what make a misrouted gateway-internal request observable (see
  // class javadoc)
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS)
          .build();

  // instance-scoped: the test writes state (deployments, instances, jobs) that must not leak into
  // another run; maxMessageSize is a cluster-wide setting (it may not be overridden per physical
  // tenant), so the small limit applies to tenant A's engine and the gateway alike
  @TestZeebe(purgeAfterEach = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withRecordingExporter(true)
              .withClusterConfig(c -> c.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE)));

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    // the extension resets this to 5s before each test; gateway-issued FAIL commands can take
    // longer than that to propagate on slow CI runners
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
    defaultClient =
        TENANTS
            .newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
            .preferRestOverGrpc(false)
            .build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).preferRestOverGrpc(false).build();
  }

  @Test
  void shouldReactivateDeferredJobsOnTheirOwnPhysicalTenant() {
    // given — jobs with large variables on tenant A, so that one partition's activate-jobs
    // response exceeds the gateway's maxMessageSize
    awaitTenantAPartitionsReady();
    deployProcessToTenantA();
    final Set<Long> createdJobKeys = createJobsOnTenantA();

    // when — activating everything over gRPC: the gateway defers the jobs that do not fit into
    // the response and FAILs them back to the broker
    final Set<Long> activatedJobKeys =
        activateJobs(tenantAClient).stream().map(ActivatedJob::getKey).collect(Collectors.toSet());

    // then — at least one job did not fit and was FAILed back through the gateway-internal
    // reactivation path under test
    assertThat(activatedJobKeys)
        .as("the first activation cannot return every job within maxMessageSize")
        .hasSizeLessThan(JOBS_TO_CREATE);
    assertThat(RecordingExporter.jobRecords(JobIntent.FAILED).withType(JOB_TYPE).exists())
        .as("at least one deferred job was FAILed back by the gateway")
        .isTrue();

    // and — every deferred job becomes activatable again on tenant A well before its activation
    // timeout: the gateway's FAIL request reached tenant A's partition group, not the default one
    final Set<Long> reactivatableJobKeys = new HashSet<>(activatedJobKeys);
    await("deferred jobs become activatable again on tenant A")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              activateJobs(tenantAClient).forEach(job -> reactivatableJobKeys.add(job.getKey()));
              assertThat(reactivatableJobKeys).containsExactlyInAnyOrderElementsOf(createdJobKeys);
            });

    // and — nothing leaked to the default tenant: it has no activatable jobs, and no FAIL command
    // was ever rejected (a FAIL misrouted to the default group would be rejected there because the
    // job key is unknown to its engine)
    assertThat(
            defaultClient
                .newActivateJobsCommand()
                .jobType(JOB_TYPE)
                .maxJobsToActivate(JOBS_TO_CREATE)
                .timeout(JOB_TIMEOUT)
                .requestTimeout(Duration.ofSeconds(2))
                .send()
                .join()
                .getJobs())
        .as("the default tenant has no activatable jobs")
        .isEmpty();
    final boolean failRejected =
        RecordingExporter.expectNoMatchingRecords(
            records ->
                records.jobRecords().onlyCommandRejections().withIntent(JobIntent.FAIL).exists());
    assertThat(failRejected)
        .as("no gateway-issued FAIL command was rejected on any tenant")
        .isFalse();
  }

  /**
   * Waits until every partition of tenant A has a leader. Instance creation is round-robined over
   * all of the tenant's partitions, so a single not-yet-ready partition fails the setup.
   */
  private void awaitTenantAPartitionsReady() {
    await("tenant A runs %d partitions with a leader each".formatted(TENANT_A_PARTITIONS))
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(tenantAClient.newTopologyRequest().send().join())
                    .isComplete(1, TENANT_A_PARTITIONS, 1));
  }

  private void deployProcessToTenantA() {
    await("deployment to tenant A succeeds")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        tenantAClient
                            .newDeployResourceCommand()
                            .addProcessModel(PROCESS, PROCESS_ID + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
  }

  /** Creates {@link #JOBS_TO_CREATE} instances and returns the keys of their created jobs. */
  private Set<Long> createJobsOnTenantA() {
    final var quoteHeavyPayload = "\"".repeat(QUOTE_PAYLOAD_LENGTH);
    for (int i = 0; i < JOBS_TO_CREATE; i++) {
      tenantAClient
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .variables(Map.of("message_content", quoteHeavyPayload))
          .send()
          .join();
    }
    final Set<Long> createdJobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(JOB_TYPE)
            .limit(JOBS_TO_CREATE)
            .map(Record::getKey)
            .collect(Collectors.toSet());
    assertThat(createdJobKeys).as("all jobs are created before activation").hasSize(JOBS_TO_CREATE);
    return createdJobKeys;
  }

  private List<ActivatedJob> activateJobs(final CamundaClient client) {
    return client
        .newActivateJobsCommand()
        .jobType(JOB_TYPE)
        .maxJobsToActivate(JOBS_TO_CREATE)
        .timeout(JOB_TIMEOUT)
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .join()
        .getJobs();
  }
}
