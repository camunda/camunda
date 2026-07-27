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
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof that the broker's tenant-scoped {@code jobsAvailable} notification
 * (physical-tenant-aware long polling, issue #56224) actually reaches the gateway's {@code
 * LongPollingActivateJobsHandler} on the wire: the broker formats the topic as {@code
 * <physicalTenantId>-jobsAvailable} and the gateway subscribes to that same string independently on
 * its own side, so nothing but a matching literal proves the two sides agree.
 *
 * <p>The probe timeout is set far longer than the assertion window, so a blocked long-poll request
 * can only complete in time via the notification path; if the topic strings ever drifted apart,
 * this test would time out waiting on the (empty) probe cycle instead.
 */
@ZeebeIntegration
final class PhysicalTenantJobAvailableNotificationIT {

  private static final String TENANT_A = "tenanta";
  private static final String JOB_TYPE = "task";
  private static final String PROCESS_ID = "notification-process";

  // far longer than the assertion window below, so only the job-available notification -- not the
  // periodic probe -- can plausibly unblock the long-poll request in time
  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration NOTIFICATION_ASSERTION_WINDOW = Duration.ofSeconds(10);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withUnifiedConfig(
                  camunda ->
                      camunda.getApi().getLongPolling().setProbeTimeout(PROBE_TIMEOUT.toMillis())));

  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
  }

  @Test
  void shouldUnblockLongPollRequestViaTenantScopedNotificationFasterThanTheProbe() {
    // given - a process deployed to tenant A, and a long-poll ActivateJobs request already
    // blocked (no job exists yet) before the process instance is created
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    await("deployment to tenant A succeeds")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        tenantAClient
                            .newDeployResourceCommand()
                            .addProcessModel(process, PROCESS_ID + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());

    final var jobs =
        tenantAClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .requestTimeout(PROBE_TIMEOUT.plusSeconds(30))
            .send();

    // when - a job becomes available in tenant A only after the request is already blocked
    final long processInstanceKey =
        tenantAClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // then - the request completes well before the probe would ever fire, so it can only have been
    // unblocked by the tenant-scoped notification reaching the gateway
    assertThat((CompletionStage<ActivateJobsResponse>) jobs)
        .succeedsWithin(NOTIFICATION_ASSERTION_WINDOW.toSeconds(), TimeUnit.SECONDS)
        .satisfies(
            response -> {
              assertThat(response.getJobs()).hasSize(1);
              assertThat(response.getJobs().getFirst().getProcessInstanceKey())
                  .isEqualTo(processInstanceKey);
            });
  }
}
