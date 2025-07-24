/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.DELETE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateV1ApiPermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withAdditionalProfile(Profile.OPERATE);

  private static final String PROCESS_ID = "processId";
  private static final String READ_AUTHORIZED_USERNAME = "operateV1ReadAuthorizedUser";
  private static final String DELETE_AUTHORIZED_USERNAME = "operateV1DeleteAuthorizedUser";
  private static final String UNAUTHORIZED_USERNAME = "operateV1UnauthorizedUser";
  private static long processInstanceKey;
  private static long processInstanceToDeleteKey;
  private static long processDefinitionKey;
  private static long flowNodeInstanceKey;

  @AutoClose
  private static final TestRestOperateClient READ_AUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(READ_AUTHORIZED_USERNAME, READ_AUTHORIZED_USERNAME);

  @AutoClose
  private static final TestRestOperateClient MODIFY_AUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(DELETE_AUTHORIZED_USERNAME, DELETE_AUTHORIZED_USERNAME);

  @AutoClose
  private static final TestRestOperateClient UNAUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME);

  @UserDefinition
  private static final TestUser READ_AUTHORIZED_USER =
      new TestUser(
          READ_AUTHORIZED_USERNAME,
          READ_AUTHORIZED_USERNAME,
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser MODIFY_AUTHORIZED_USER =
      new TestUser(
          DELETE_AUTHORIZED_USERNAME,
          DELETE_AUTHORIZED_USERNAME,
          List.of(new Permissions(PROCESS_DEFINITION, DELETE_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  @BeforeAll
  public static void beforeAll(final CamundaClient adminClient) throws Exception {

    // deploy process and start instance
    processDefinitionKey =
        deployResource(
                adminClient,
                Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done(),
                "process.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    waitForProcessesToBeDeployed(adminClient, 1);
    processInstanceKey = startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    processInstanceToDeleteKey =
        startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToStart(adminClient, f -> f.processDefinitionId(PROCESS_ID), 2);
    adminClient.newCancelInstanceCommand(processInstanceToDeleteKey).send().join();
    waitForProcessInstanceToBeTerminated(adminClient, processInstanceToDeleteKey);
    flowNodeInstanceKey =
        adminClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items()
            .getFirst()
            .getElementInstanceKey();
  }

  // Process Instances
  @Test
  void shouldBeUnauthorizedToGetProcessInstanceUsingV1Api() throws Exception {
    final int statusCode =
        UNAUTHORIZED_OPERATE_CLIENT
            .getRequest("v1/process-instances/%s", processInstanceKey)
            .statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the process instance")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void shouldBePermittedToSearchProcessInstanceUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        READ_AUTHORIZED_OPERATE_CLIENT.searchRequest("v1/process-instances", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to get the process instance")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToDeleteProcessInstanceUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        READ_AUTHORIZED_OPERATE_CLIENT
            .deleteRequest("v1/process-instances", processInstanceKey)
            .statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to delete the process instance")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  @Disabled // depends on fix for https://github.com/camunda/camunda/issues/36067
  void shouldBePermittedToDeleteProcessInstanceUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        MODIFY_AUTHORIZED_OPERATE_CLIENT
            .deleteRequest("v1/process-instances", processInstanceToDeleteKey)
            .statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to delete the process instance")
        .isEqualTo(HttpStatus.OK.value());
  }

  // Process Instances
  @Test
  void shouldBePermittedToGetProcessDefinitionsUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        READ_AUTHORIZED_OPERATE_CLIENT
            .getRequest("v1/process-definitions/%s", processDefinitionKey)
            .statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to get the process definitions")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToSearchProcessDefinitionsUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        UNAUTHORIZED_OPERATE_CLIENT.searchRequest("v1/process-definitions", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the process definition")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  // Flownode Instaneces
  @Test
  void shouldBeUnauthorizedToGetFlowNodeInstanceUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        UNAUTHORIZED_OPERATE_CLIENT
            .getRequest("v1/flownode-instances/%s", flowNodeInstanceKey)
            .statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the flownode instance")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void shouldBePermittedToSearchFlowNodeInstanceUsingV1Api(final CamundaClient client)
      throws Exception {
    final int statusCode =
        READ_AUTHORIZED_OPERATE_CLIENT.searchRequest("v1/flownode-instances", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to search flownode instances")
        .isEqualTo(HttpStatus.OK.value());
  }
}
