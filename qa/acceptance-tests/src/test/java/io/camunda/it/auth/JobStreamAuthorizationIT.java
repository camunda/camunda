/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class JobStreamAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static CamundaClient adminClient;

  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String PROCESS_ID_1 = "service_tasks_v1";
  private static final String PROCESS_ID_2 = "service_tasks_v2";
  private static final String PROCESS_ID_3 = "service_tasks_v3";
  private static final String PROCESS_ID_4 = "service_tasks_v4";
  private static final String PROCESS_ID_5 = "service_tasks_v5";
  private static final String JOB_TYPE = "taskA";
  private static final String JOB_TYPE_B = "taskB";
  private static final String USER1_USERNAME = "user1";
  private static final String USER2_USERNAME = "user2";

  private static final Set<Long> STARTED_PROCESS_INSTANCES = new HashSet<>();

  @UserDefinition
  private static final TestUser USER1_USER =
      new TestUser(USER1_USERNAME, USER1_USERNAME, List.of());

  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          USER2_USERNAME,
          USER2_USERNAME,
          List.of(
              new Permissions(
                  PROCESS_DEFINITION,
                  READ_PROCESS_INSTANCE,
                  List.of(PROCESS_ID_2, PROCESS_ID_4, PROCESS_ID_5)),
              new Permissions(
                  PROCESS_DEFINITION,
                  UPDATE_PROCESS_INSTANCE,
                  List.of(PROCESS_ID_2, PROCESS_ID_4, PROCESS_ID_5))));

  @BeforeAll
  static void setUp() {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);

    assignUserToTenant(adminClient, "demo", TENANT_A);
    assignUserToTenant(adminClient, "demo", TENANT_B);
    // user1 is only assigned to tenantA, but isn't authorized to handle any resources
    assignUserToTenant(adminClient, USER1_USERNAME, TENANT_A);
    // user2 is assigned to tenantA AND tenant B BUT is authorized to handle only PROCESS_2 on
    // tenantB
    assignUserToTenant(adminClient, USER2_USERNAME, TENANT_A);
    assignUserToTenant(adminClient, USER2_USERNAME, TENANT_B);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);

    final var modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID_2)
            .startEvent()
            .serviceTask(JOB_TYPE, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    deployResource(adminClient, "service_tasks_v2.bpmn", modelInstance, TENANT_B);

    // Deploy processes with JOB_TYPE_B for TenantFilter.ASSIGNED tests (isolated from taskA tests)
    final var processV3 =
        Bpmn.createExecutableProcess(PROCESS_ID_3)
            .startEvent()
            .serviceTask(JOB_TYPE_B, t -> t.zeebeJobType(JOB_TYPE_B))
            .endEvent()
            .done();
    deployResource(adminClient, "service_tasks_v3.bpmn", processV3, TENANT_A);
    deployResource(adminClient, "service_tasks_v3.bpmn", processV3, TENANT_B);

    final var processV4 =
        Bpmn.createExecutableProcess(PROCESS_ID_4)
            .startEvent()
            .serviceTask(JOB_TYPE_B, t -> t.zeebeJobType(JOB_TYPE_B))
            .endEvent()
            .done();
    deployResource(adminClient, "service_tasks_v4.bpmn", processV4, TENANT_B);

    final var processV5 =
        Bpmn.createExecutableProcess(PROCESS_ID_5)
            .startEvent()
            .serviceTask(JOB_TYPE_B, t -> t.zeebeJobType(JOB_TYPE_B))
            .endEvent()
            .done();
    deployResource(adminClient, "service_tasks_v5.bpmn", processV5, TENANT_B);
  }

  @AfterEach
  void cleanUp() {
    // cancel all process instances to ensure no jobs are left in the system
    STARTED_PROCESS_INSTANCES.forEach(
        processInstanceKey -> cancelProcessInstance(adminClient, processInstanceKey));
    STARTED_PROCESS_INSTANCES.clear();
  }

  @Disabled("We don't have a broker mechanism to reject unauthorized job streams yet")
  @Test
  public void shouldNotOpenStreamWhenNotAssignedToAllTenants(
      @Authenticated(USER1_USERNAME) final CamundaClient camundaClient) {
    // given
    // a job set for collecting jobs in the client
    final var jobCollector = new HashSet<ActivatedJob>();
    // and a job stream command created by the user1 client, with their authorizations
    final var command =
        camundaClient
            .newStreamJobsCommand()
            .jobType(JOB_TYPE)
            .consumer(job -> jobCollector.add(job))
            .tenantIds(TENANT_A, TENANT_B);

    // when
    // a stream is opened
    assertThatThrownBy(() -> command.send().cancel(true))
        // then
        .hasMessageContaining(
            "Expected to find authorizations for all tenants, but found only for tenants: [%s]"
                .formatted(TENANT_A));
  }

  @Test
  public void shouldReceiveNoJobsWhenNotAuthorized(
      @Authenticated(USER1_USERNAME) final CamundaClient user1Client) {
    // given
    // a job set for collecting jobs in the client
    final var jobCollector = new HashSet<ActivatedJob>();
    // and a job stream created by the user1 client, with their authorizations
    final var stream =
        user1Client
            .newStreamJobsCommand()
            .jobType(JOB_TYPE)
            .consumer(job -> jobCollector.add(job))
            .tenantId(TENANT_A)
            .send();

    // when
    // two process instances with jobs are created (one for each tenant)
    try {
      startProcessInstance(adminClient, PROCESS_ID_1, TENANT_A);
      startProcessInstance(adminClient, PROCESS_ID_1, TENANT_B);
      waitForJobsBeingExported(adminClient, 2, JobState.CREATED, PROCESS_ID_1);

      // then
      // expect that  the camunda client of user1 receives no job
      assertThat(jobCollector).isEmpty();
    } finally {
      // ensure that the stream is closed
      stream.cancel(true);
    }
  }

  @Test
  public void shouldReceiveOnlyAuthorizedJobs(
      @Authenticated(USER2_USERNAME) final CamundaClient user2Client) {
    // given
    // a job set for collecting jobs in the client
    final var jobCollector = new HashSet<ActivatedJob>();
    // and a job stream created by the user2 client, with their authorizations
    final var stream =
        user2Client
            .newStreamJobsCommand()
            .jobType(JOB_TYPE)
            .consumer(
                job -> {
                  user2Client.newCompleteCommand(job).send().join();
                  jobCollector.add(job);
                  STARTED_PROCESS_INSTANCES.remove(job.getProcessInstanceKey());
                })
            .tenantIds(TENANT_A, TENANT_B)
            .send();

    // when
    // two process instances with jobs are created (one for each tenant)
    try {
      startProcessInstance(adminClient, PROCESS_ID_1, TENANT_A);
      startProcessInstance(adminClient, PROCESS_ID_1, TENANT_B);
      startProcessInstance(adminClient, PROCESS_ID_2, TENANT_B);

      // then
      // expect that only one job is completed
      waitForJobsBeingExported(adminClient, 1, JobState.COMPLETED, PROCESS_ID_1, PROCESS_ID_2);
      // expect that  the camunda client of user2 receives and completes that one job
      assertThat(jobCollector).hasSize(1);
      assertThat(jobCollector.iterator().next().getTenantId()).isEqualTo(TENANT_B);
    } finally {
      // ensure that the stream is closed
      stream.cancel(true);
    }
  }

  @Test
  public void shouldReceiveNoJobsWhenNotAuthorizedWithAssignedTenantFilter(
      @Authenticated(USER1_USERNAME) final CamundaClient user1Client) {
    // given
    final var jobCollector = new HashSet<ActivatedJob>();
    // a job stream created by user1 with ASSIGNED tenant filter (resolves to tenantA only)
    final var stream =
        user1Client
            .newStreamJobsCommand()
            .jobType(JOB_TYPE_B)
            .consumer(job -> jobCollector.add(job))
            .tenantFilter(TenantFilter.ASSIGNED)
            .send();

    // when
    try {
      startProcessInstance(adminClient, PROCESS_ID_3, TENANT_A);
      startProcessInstance(adminClient, PROCESS_ID_3, TENANT_B);
      waitForJobsBeingExported(adminClient, 2, JobState.CREATED, PROCESS_ID_3);

      // then
      // user1 has no permissions, so no jobs should be received
      assertThat(jobCollector).isEmpty();
    } finally {
      stream.cancel(true);
    }
  }

  @Test
  public void shouldReceiveOnlyAuthorizedJobsWithAssignedTenantFilter(
      @Authenticated(USER2_USERNAME) final CamundaClient user2Client) {
    // given
    final var jobCollector = new HashSet<ActivatedJob>();
    // a job stream created by user2 with ASSIGNED tenant filter (resolves to tenantA and tenantB)
    final var stream =
        user2Client
            .newStreamJobsCommand()
            .jobType(JOB_TYPE_B)
            .consumer(
                job -> {
                  user2Client.newCompleteCommand(job).send().join();
                  jobCollector.add(job);
                  STARTED_PROCESS_INSTANCES.remove(job.getProcessInstanceKey());
                })
            .tenantFilter(TenantFilter.ASSIGNED)
            .send();

    // when
    try {
      startProcessInstance(adminClient, PROCESS_ID_3, TENANT_A);
      startProcessInstance(adminClient, PROCESS_ID_3, TENANT_B);
      startProcessInstance(adminClient, PROCESS_ID_4, TENANT_B);

      // then
      // user2 is only authorized for PROCESS_ID_4 on tenantB
      waitForJobsBeingExported(adminClient, 1, JobState.COMPLETED, PROCESS_ID_3, PROCESS_ID_4);
      assertThat(jobCollector).hasSize(1);
      assertThat(jobCollector.iterator().next().getTenantId()).isEqualTo(TENANT_B);
    } finally {
      stream.cancel(true);
    }
  }

  @Test
  public void shouldIgnoreProvidedTenantIdsWhenAssignedTenantFilterIsSet(
      @Authenticated(USER2_USERNAME) final CamundaClient user2Client) {
    // given
    final var jobCollector = new HashSet<ActivatedJob>();
    // a job stream with ASSIGNED filter AND an explicit tenantId(TENANT_A) —
    // the ASSIGNED filter should override the provided tenant IDs, so the broker
    // resolves from all assigned tenants (both A and B), not just tenantA
    final var stream =
        user2Client
            .newStreamJobsCommand()
            .jobType(JOB_TYPE_B)
            .consumer(
                job -> {
                  user2Client.newCompleteCommand(job).send().join();
                  jobCollector.add(job);
                  STARTED_PROCESS_INSTANCES.remove(job.getProcessInstanceKey());
                })
            .tenantId(TENANT_A)
            .tenantFilter(TenantFilter.ASSIGNED)
            .send();

    // when
    try {
      startProcessInstance(adminClient, PROCESS_ID_5, TENANT_B);

      // then
      // if ASSIGNED didn't override the tenant IDs, the stream would be limited to tenantA
      // and would miss this job on tenantB
      waitForJobsBeingExported(adminClient, 1, JobState.COMPLETED, PROCESS_ID_5);
      assertThat(jobCollector).hasSize(1);
      assertThat(jobCollector.iterator().next().getTenantId()).isEqualTo(TENANT_B);
    } finally {
      stream.cancel(true);
    }
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void deployResource(
      final CamundaClient camundaClient, final String resourceName, final String tenant) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void deployResource(
      final CamundaClient camundaClient,
      final String resourceName,
      final BpmnModelInstance modelInstance,
      final String tenant) {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, resourceName)
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    final var instanceCreated =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .tenantId(tenant)
            .send()
            .join();
    STARTED_PROCESS_INSTANCES.add(instanceCreated.getProcessInstanceKey());
  }

  private static void cancelProcessInstance(
      final CamundaClient camundaClient, final long processInstanceKey) {
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  private static void waitForJobsBeingExported(
      final CamundaClient camundaClient,
      final int expectedJobs,
      final JobState state,
      final String... resourceIds) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newJobSearchRequest()
                          .filter(
                              filter ->
                                  filter.processDefinitionId(fn -> fn.in(resourceIds)).state(state))
                          .send()
                          .join()
                          .items())
                  .hasSize(expectedJobs);
            });
  }
}
