/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateV1ApiRolePermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withAdditionalProfile(Profile.OPERATE)
          .withProperty("camunda.tasklist.webappEnabled", "false");

  private static final String AUTHORIZED_USERNAME = "operateV1RoleAuthorizedUser";
  private static final String UNAUTHORIZED_USERNAME = "operateV1RoleUnauthorizedUser";
  private static final String PROCESS_ID = "processId";
  private static long processInstanceKey;

  @AutoClose
  private static final TestRestOperateClient AUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(AUTHORIZED_USERNAME, AUTHORIZED_USERNAME);

  @AutoClose
  private static final TestRestOperateClient UNAUTHORIZED_OPERATE_CLIENT =
      STANDALONE_CAMUNDA.newOperateClient(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME);

  @UserDefinition
  private static final TestUser AUTHORIZED_USER =
      new TestUser(AUTHORIZED_USERNAME, AUTHORIZED_USERNAME, List.of());

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  private static final String ROLE_ID = Strings.newRandomValidIdentityId();

  @RoleDefinition
  private static final TestRole AUTHORIZED_ROLE =
      new TestRole(
          ROLE_ID,
          "operate_v1_auth_role",
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))),
          List.of(new Membership(AUTHORIZED_USERNAME, EntityType.USER)));

  @BeforeAll
  public static void beforeAll(final CamundaClient adminClient) throws Exception {
    // deploy process and start instance
    deployResource(
        adminClient,
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done(),
        "process.bpmn");
    waitForProcessesToBeDeployed(adminClient, 1);
    processInstanceKey = startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToStart(adminClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // add variable
    adminClient
        .newSetVariablesCommand(processInstanceKey)
        .variable("testVariableA", "a")
        .local(false)
        .send()
        .join();
    await()
        .untilAsserted(
            () ->
                assertThat(adminClient.newJobSearchRequest().send().join().items())
                    .describedAs("Wait until job exists")
                    .hasSize(1));

    // create incident
    final long jobKey =
        adminClient.newJobSearchRequest().send().join().items().getFirst().getJobKey();
    adminClient.newFailCommand(jobKey).retries(0).send().join();
    TestHelper.waitUntilProcessInstanceHasIncidents(adminClient, 1);
  }

  // Incidents
  @Test
  void shouldBePermittedToSearchIncidentUsingV1Api() throws Exception {
    final int statusCode =
        AUTHORIZED_OPERATE_CLIENT.searchRequest("v1/incidents", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to search incidents")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToGetIncidentUsingV1Api() throws Exception {
    final int statusCode =
        UNAUTHORIZED_OPERATE_CLIENT.getRequest("v1/incidents/%s", 1L).statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the incident")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  // Variables
  @Test
  void shouldBePermittedToSearchVariablesUsingV1Api() throws Exception {
    final int statusCode =
        AUTHORIZED_OPERATE_CLIENT.searchRequest("v1/variables", "{}").statusCode();
    assertThat(statusCode)
        .describedAs("Is authorized to get variable")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToGetVariablesUsingV1Api() throws Exception {
    final int statusCode =
        UNAUTHORIZED_OPERATE_CLIENT.getRequest("v1/variables/%s", 1L).statusCode();
    assertThat(statusCode)
        .describedAs("Is unauthorized to get variables")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }
}
