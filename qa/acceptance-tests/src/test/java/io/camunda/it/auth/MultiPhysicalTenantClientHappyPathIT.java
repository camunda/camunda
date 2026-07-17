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
    final String jobTypeA = "job-" + processA;
    final String jobTypeB = "job-" + processB;

    // given — each client deploys its own process and creates an instance (commands)
    deployServiceTaskProcess(tenantA, processA, jobTypeA);
    deployServiceTaskProcess(tenantB, processB, jobTypeB);
    tenantA.newCreateInstanceCommand().bpmnProcessId(processA).latestVersion().send().join();
    tenantB.newCreateInstanceCommand().bpmnProcessId(processB).latestVersion().send().join();

    // then — each client activates only its own physical tenant's job (jobs)
    Awaitility.await("tenantA activates its own job")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(activateJobType(tenantA, jobTypeA)).isEqualTo(1));
    Awaitility.await("tenantB activates its own job")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(activateJobType(tenantB, jobTypeB)).isEqualTo(1));

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

  private static int activateJobType(final CamundaClient client, final String jobType) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .send()
        .join()
        .getJobs()
        .size();
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
