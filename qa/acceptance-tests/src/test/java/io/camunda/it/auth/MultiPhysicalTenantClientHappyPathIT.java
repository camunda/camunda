/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Multi-client happy path over a real runtime: two clients, each scoped to its own physical tenant,
 * run commands (deploy, create instance), jobs (activate) and queries (search) independently and
 * see only their own physical tenant's data.
 *
 * <p>Complements the client-side WireMock integration tests in {@code camunda-spring-boot-starter}
 * (per-client REST routing, base-URL modes, {@code @JobWorker} fan-out) with an end-to-end check
 * against a running broker.
 *
 * <p>RDBMS only — physical-tenant secondary storage is RDBMS-only, matching the other
 * physical-tenant acceptance tests.
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Physical-tenant secondary storage is RDBMS-only")
final class MultiPhysicalTenantClientHappyPathIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  static MultiPhysicalTenantClients ptClients;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  @Test
  void shouldRunCommandsJobsAndQueriesPerPhysicalTenantClient() {
    final CamundaClient tenantA = ptClients.admin(TENANT_A);
    final CamundaClient tenantB = ptClients.admin(TENANT_B);
    final String processA = Strings.newRandomValidBpmnId();
    final String processB = Strings.newRandomValidBpmnId();
    // deliberately the SAME job type in both tenants: each client requests this shared type, so a
    // routing leak would surface as one client activating the other tenant's job
    final String jobType = "shared-job-" + Strings.newRandomValidBpmnId();

    // given — each client deploys its own process (same job type) and creates an instance
    // (commands)
    deployServiceTaskProcess(tenantA, processA, jobType);
    deployServiceTaskProcess(tenantB, processB, jobType);
    tenantA.newCreateInstanceCommand().bpmnProcessId(processA).latestVersion().send().join();
    tenantB.newCreateInstanceCommand().bpmnProcessId(processB).latestVersion().send().join();

    // then — requesting the shared job type, each client activates only its own tenant's job and
    // never the other tenant's, proving job routing is isolated per physical tenant (jobs)
    Awaitility.await("tenantA activates only its own tenant's jobs for the shared type")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final List<String> processes = activatedJobProcesses(tenantA, jobType);
              assertThat(processes).containsExactly(processA);
            });
    Awaitility.await("tenantB activates only its own tenant's jobs for the shared type")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final List<String> processes = activatedJobProcesses(tenantB, jobType);
              assertThat(processes).containsExactly(processB);
            });

    // and — each client sees only its own instance via search (queries)
    Awaitility.await("each client sees only its own process instance")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(activeInstanceCount(tenantA, processA)).isEqualTo(1);
              assertThat(activeInstanceCount(tenantB, processB)).isEqualTo(1);
            });

    // and — the physical-tenant boundary holds: neither client sees the other physical tenant's
    // instance, continuously over a window so a late-arriving leak still fails
    Awaitility.await("physical-tenant instance data does not leak across clients")
        .during(PROPAGATION_TIMEOUT.dividedBy(6))
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(activeInstanceCount(tenantA, processB)).isZero();
              assertThat(activeInstanceCount(tenantB, processA)).isZero();
            });
  }

  private static void deployServiceTaskProcess(
      final CamundaClient client, final String processId, final String jobType) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(model, processId + ".bpmn").send().join();
  }

  /**
   * Activates up to a handful of jobs of the given type and returns the BPMN process ids they came
   * from. A high {@code maxJobsToActivate} ensures a routing leak (the other tenant's job) would be
   * included rather than silently missed.
   */
  private static List<String> activatedJobProcesses(
      final CamundaClient client, final String jobType) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(10)
        .send()
        .join()
        .getJobs()
        .stream()
        .map(ActivatedJob::getBpmnProcessId)
        .toList();
  }

  private static long activeInstanceCount(final CamundaClient client, final String processId) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId).state(ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .items()
        .size();
  }
}
