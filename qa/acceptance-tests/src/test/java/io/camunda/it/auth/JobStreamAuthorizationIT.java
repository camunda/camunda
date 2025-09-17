/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class JobStreamAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String PROCESS_ID_1 = "service_tasks_v1";
  private static final String PROCESS_ID_2 = "service_tasks_v2";
  private static final String JOB_TYPE = "taskA";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          "admin",
          "password",
          List.of(
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, UPDATE, List.of("*")),
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser USER1_USER = new TestUser("user1", "password", List.of());

  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          "user2",
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_2)),
              new Permissions(PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of(PROCESS_ID_2))));

  @BeforeAll
  static void setUp(@Authenticated("admin") final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);

    assignUserToTenant(adminClient, "admin", TENANT_A);
    assignUserToTenant(adminClient, "admin", TENANT_B);
    // user1 is only assigned to tenantA, but isn't authorized to handle any resources
    assignUserToTenant(adminClient, "user1", TENANT_A);
    // user2 is assigned to tenantA AND tenant B BUT is authorized to handle only PROCESS_2 on
    // tenantB
    assignUserToTenant(adminClient, "user2", TENANT_A);
    assignUserToTenant(adminClient, "user2", TENANT_B);

    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_A);
    deployResource(adminClient, "process/service_tasks_v1.bpmn", TENANT_B);

    final var modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID_2)
            .startEvent()
            .serviceTask(JOB_TYPE, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    deployResource(adminClient, "service_tasks_v2.bpmn", modelInstance, TENANT_B);
  }

  @Disabled("We don't have a broker mechanism to reject unauthorized job streams yet")
  @Test
  public void shouldNotOpenStreamWhenNotAssignedToAllTenants(
      @Authenticated("admin") final CamundaClient adminClient,
      @Authenticated("user1") final CamundaClient camundaClient) {
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
            "Expected to find authorizations for all tenants, but found only for tenants: [tenantA]");
  }

  @Test
  public void shouldReceiveNoJobsWhenNotAuthorized(
      @Authenticated("admin") final CamundaClient adminClient,
      @Authenticated("user1") final CamundaClient camundaClient) {
    // given
    // a job set for collecting jobs in the client
    final var jobCollector = new HashSet<ActivatedJob>();
    // and a job stream created by the user1 client, with their authorizations
    final var stream =
        camundaClient
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
      waitForJobsBeingExported(adminClient, 2, PROCESS_ID_1);

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
      @Authenticated("admin") final CamundaClient adminClient,
      @Authenticated("user2") final CamundaClient camundaClient) {
    // given
    // a job set for collecting jobs in the client
    final var jobCollector = new HashSet<ActivatedJob>();
    // and a job stream created by the user1 client, with their authorizations
    final var stream =
        camundaClient
            .newStreamJobsCommand()
            .jobType(JOB_TYPE)
            .consumer(job -> jobCollector.add(job))
            .tenantIds(TENANT_A, TENANT_B)
            .send();

    // when
    // two process instances with jobs are created (one for each tenant)
    try {
      startProcessInstance(adminClient, PROCESS_ID_1, TENANT_A);
      startProcessInstance(adminClient, PROCESS_ID_1, TENANT_B);
      startProcessInstance(adminClient, PROCESS_ID_2, TENANT_B);
      waitForJobsBeingExported(adminClient, 3, PROCESS_ID_1, PROCESS_ID_2);

      // then
      // expect that  the camunda client of user2 receives one job
      assertThat(jobCollector).hasSize(1);
      assertThat(jobCollector.iterator().next().getTenantId()).isEqualTo(TENANT_B);
    } finally {
      // ensure that the stream is closed
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
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void waitForJobsBeingExported(
      final CamundaClient camundaClient, final int expectedJobs, final String... resourceIds) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newJobSearchRequest()
                          .filter(filter -> filter.processDefinitionId(fn -> fn.in(resourceIds)))
                          .send()
                          .join()
                          .items())
                  .hasSize(expectedJobs);
            });
  }
}
