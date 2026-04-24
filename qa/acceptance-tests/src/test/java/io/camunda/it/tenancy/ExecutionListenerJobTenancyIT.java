/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ExecutionListenerJobTenancyIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String ADMIN = "admin";
  private static final String USER_A = "user-a";
  private static final String USER_B = "user-b";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";

  private static final String PROCESS_ID = "before_all_el_tenancy_process";
  private static final String SERVICE_TASK_JOB_TYPE = "mi-service-task-job";
  private static final String BEFORE_ALL_EL_JOB_TYPE = "before-all-execution-listener";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER_A_USER = new TestUser(USER_A, "password", List.of());

  @UserDefinition
  private static final TestUser USER_B_USER = new TestUser(USER_B, "password", List.of());

  @TenantDefinition
  private static final TestTenant A_TENANT =
      new TestTenant(TENANT_A).setName(TENANT_A).addUsers(ADMIN, USER_A);

  @TenantDefinition
  private static final TestTenant B_TENANT =
      new TestTenant(TENANT_B).setName(TENANT_B).addUsers(ADMIN, USER_B);

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {

    // deploy the same multi-instance + beforeAll listener process under BOTH tenants and start
    // one instance per tenant so each emits a beforeAll listener job
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "mi_service_task",
                t ->
                    t.zeebeJobType(SERVICE_TASK_JOB_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_JOB_TYPE)
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    deployAndStart(adminClient, process, TENANT_A);
    deployAndStart(adminClient, process, TENANT_B);

    // wait until the beforeAll listener jobs of BOTH tenants are visible in secondary storage
    Awaitility.await("both tenants' beforeAll listener jobs are searchable")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var allJobs =
                  adminClient
                      .newJobSearchRequest()
                      .filter(
                          f ->
                              f.type(BEFORE_ALL_EL_JOB_TYPE)
                                  .listenerEventType(ListenerEventType.BEFORE_ALL))
                      .send()
                      .join()
                      .items();
              assertThat(allJobs).hasSize(2);
            });
  }

  @Test
  public void shouldReturnBothTenantsBeforeAllListenerJobsForAdmin(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when — admin (assigned to both tenants) searches for beforeAll listener jobs
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f -> f.type(BEFORE_ALL_EL_JOB_TYPE).listenerEventType(ListenerEventType.BEFORE_ALL))
            .send()
            .join();

    // then — admin sees one beforeAll job per tenant (tenant A and tenant B)
    assertThat(result.items())
        .as("admin must see the beforeAll listener job of every assigned tenant")
        .hasSize(2)
        .extracting(Job::getTenantId)
        .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
  }

  @Test
  public void shouldReturnOnlyTenantABeforeAllListenerJobForUserA(
      @Authenticated(USER_A) final CamundaClient camundaClient) {
    // when — user A (assigned to tenant A only) searches for beforeAll listener jobs
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f -> f.type(BEFORE_ALL_EL_JOB_TYPE).listenerEventType(ListenerEventType.BEFORE_ALL))
            .send()
            .join();

    // then — user A only sees tenant A's listener job; tenant B's job is invisible
    assertThat(result.items())
        .as("user assigned to tenant A must only see tenant A's beforeAll listener job")
        .hasSize(1)
        .first()
        .satisfies(
            j -> {
              assertThat(j.getTenantId()).isEqualTo(TENANT_A);
              assertThat(j.getType()).isEqualTo(BEFORE_ALL_EL_JOB_TYPE);
              assertThat(j.getKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
              assertThat(j.getListenerEventType()).isEqualTo(ListenerEventType.BEFORE_ALL);
            });
  }

  @Test
  public void shouldReturnOnlyTenantBBeforeAllListenerJobForUserB(
      @Authenticated(USER_B) final CamundaClient camundaClient) {
    // when — user B (assigned to tenant B only) searches for beforeAll listener jobs
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f -> f.type(BEFORE_ALL_EL_JOB_TYPE).listenerEventType(ListenerEventType.BEFORE_ALL))
            .send()
            .join();

    // then — user B only sees tenant B's listener job; tenant A's job is invisible
    assertThat(result.items())
        .as("user assigned to tenant B must only see tenant B's beforeAll listener job")
        .hasSize(1)
        .first()
        .satisfies(
            j -> {
              assertThat(j.getTenantId()).isEqualTo(TENANT_B);
              assertThat(j.getType()).isEqualTo(BEFORE_ALL_EL_JOB_TYPE);
              assertThat(j.getKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
              assertThat(j.getListenerEventType()).isEqualTo(ListenerEventType.BEFORE_ALL);
            });
  }

  @Test
  public void shouldReturnEmptyWhenFilteringByForeignTenantAsUserA(
      @Authenticated(USER_A) final CamundaClient camundaClient) {
    // when — user A explicitly filters by tenant B (which they are NOT assigned to)
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f ->
                    f.type(BEFORE_ALL_EL_JOB_TYPE)
                        .listenerEventType(ListenerEventType.BEFORE_ALL)
                        .tenantId(TENANT_B))
            .send()
            .join();

    // then — the result is empty; tenant filter cannot bypass the user's tenant authorization
    assertThat(result.items())
        .as("filtering by an unassigned tenant must not leak that tenant's listener jobs")
        .isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static void deployAndStart(
      final CamundaClient adminClient, final BpmnModelInstance process, final String tenant) {
    adminClient
        .newDeployResourceCommand()
        .addProcessModel(process, "before_all_el_tenancy_process.bpmn")
        .tenantId(tenant)
        .send()
        .join();

    adminClient
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }
}
