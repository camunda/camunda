/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.DELETE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startDefaultTestDecisionProcessInstance;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
          .withAdditionalProfile(Profile.OPERATE)
          .withProperty("camunda.tasklist.webappEnabled", "false");

  // endpoint urls
  private static final String PROCESS_INSTANCE_ENDPOINT = "v1/process-instances";
  private static final String PROCESS_INSTANCE_GET_ENDPOINT_PATTERN =
      PROCESS_INSTANCE_ENDPOINT + "/%s";
  private static final String PROCESS_DEFINITION_ENDPOINT = "v1/process-definitions";
  private static final String PROCESS_DEFINITION_GET_ENDPOINT_PATTERN =
      PROCESS_DEFINITION_ENDPOINT + "/%s";
  private static final String PROCESS_DEFINITION_GET_XML_ENDPOINT_PATTERN =
      PROCESS_DEFINITION_ENDPOINT + "/%s/xml";
  private static final String FLOWNODE_INSTANCES_ENDPOINT = "v1/flownode-instances";
  private static final String FLOWNODE_INSTANCES_GET_ENDPOINT_PATTERN =
      FLOWNODE_INSTANCES_ENDPOINT + "/%s";
  private static final String INCIDENT_ENDPOINT = "v1/incidents";
  private static final String INCIDENT_GET_ENDPOINT_PATTERN = INCIDENT_ENDPOINT + "/%s";
  private static final String VARIABLES_ENDPOINT = "v1/variables";
  private static final String VARIABLES_GET_ENDPOINT_PATTERN = VARIABLES_ENDPOINT + "/%s";

  private static final String DECISION_DEFINITIONS_ENDPOINT = "v1/decision-definitions";
  private static final String DECISION_DEFINITIONS_GET_ENDPOINT_PATTERN =
      DECISION_DEFINITIONS_ENDPOINT + "/%s";

  private static final String DECISION_INSTANCES_ENDPOINT = "v1/decision-instances";
  private static final String DECISION_INSTANCES_GET_ENDPOINT_PATTERN =
      DECISION_INSTANCES_ENDPOINT + "/%s";
  private static final String DECISION_REQUIREMENTS_ENDPOINT = "v1/drd";
  private static final String DECISION_REQUIREMENTS_GET_ENDPOINT_PATTERN =
      DECISION_REQUIREMENTS_ENDPOINT + "/%s";
  private static final String DECISION_REQUIREMENTS_GET_XML_ENDPOINT_PATTERN =
      DECISION_REQUIREMENTS_ENDPOINT + "/%s/xml";

  // searchable keys
  private static final String PROCESS_ID = "processId";
  private static long processInstanceKey;
  private static long processDefinitionKey;
  private static long flowNodeInstanceKey;
  private static long incidentKey;
  private static long variableKey;
  private static long decisionDefinitionKey;
  private static String decisionInstanceId;
  private static long decisionRequirementsKey;

  // Users
  private static final String AUTHORIZED_USERNAME = "operateV1AuthorizedUser";
  private static final String ROLE_AUTHORIZED_USERNAME = "operateV1RoleAuthorizedUser";
  private static final String GROUP_AUTHORIZED_USERNAME = "operateV1GroupAuthorizedUser";
  private static final String UNAUTHORIZED_USERNAME = "operateV1UnauthorizedUser";

  private static final List<Permissions> AUTHORIZED_PERMISSIONS =
      List.of(
          new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
          new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
          new Permissions(PROCESS_DEFINITION, DELETE_PROCESS_INSTANCE, List.of("*")),
          new Permissions(DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of("*")),
          new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*")),
          new Permissions(DECISION_REQUIREMENTS_DEFINITION, READ, List.of("*")));

  @UserDefinition
  private static final TestUser AUTHORIZED_USER =
      new TestUser(AUTHORIZED_USERNAME, AUTHORIZED_USERNAME, AUTHORIZED_PERMISSIONS);

  private static final String ROLE_ID = Strings.newRandomValidIdentityId();

  @RoleDefinition
  private static final TestRole AUTHORIZED_ROLE =
      new TestRole(
          ROLE_ID,
          "operate_v1_auth_role",
          AUTHORIZED_PERMISSIONS,
          List.of(new Membership(ROLE_AUTHORIZED_USERNAME, EntityType.USER)));

  @UserDefinition
  private static final TestUser ROLE_AUTHORIZED_USER =
      new TestUser(ROLE_AUTHORIZED_USERNAME, ROLE_AUTHORIZED_USERNAME, List.of());

  private static final String GROUP_ID = Strings.newRandomValidIdentityId();

  @GroupDefinition
  private static final TestGroup AUTHORIZED_GROUP =
      new TestGroup(
          GROUP_ID,
          "operate_v1_auth_group",
          AUTHORIZED_PERMISSIONS,
          List.of(new Membership(GROUP_AUTHORIZED_USERNAME, EntityType.USER)));

  @UserDefinition
  private static final TestUser GROUP_AUTHORIZED_USER =
      new TestUser(GROUP_AUTHORIZED_USERNAME, GROUP_AUTHORIZED_USERNAME, List.of());

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
    waitForElementInstances(adminClient, f -> f.processInstanceKey(processInstanceKey), 2);
    flowNodeInstanceKey =
        adminClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items()
            .getFirst()
            .getElementInstanceKey();

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
                assertThat(adminClient.newVariableSearchRequest().send().join().items())
                    .describedAs("Wait until variable exists")
                    .hasSize(1));
    variableKey =
        adminClient.newVariableSearchRequest().send().join().items().getFirst().getVariableKey();
    // create incident
    await()
        .untilAsserted(
            () ->
                assertThat(adminClient.newJobSearchRequest().send().join().items())
                    .describedAs("Wait until job exists")
                    .hasSize(1));
    final long jobKey =
        adminClient.newJobSearchRequest().send().join().items().getFirst().getJobKey();
    adminClient.newFailCommand(jobKey).retries(0).send().join();
    waitUntilProcessInstanceHasIncidents(adminClient, 1);
    incidentKey =
        adminClient.newIncidentSearchRequest().send().join().items().getFirst().getIncidentKey();

    // DMN
    startDefaultTestDecisionProcessInstance(adminClient, "decision_model.dmn");
    decisionDefinitionKey =
        adminClient
            .newDecisionDefinitionSearchRequest()
            .send()
            .join()
            .items()
            .getFirst()
            .getDecisionKey();
    decisionInstanceId =
        adminClient
            .newDecisionInstanceSearchRequest()
            .send()
            .join()
            .items()
            .getFirst()
            .getDecisionInstanceId();
    decisionRequirementsKey =
        adminClient
            .newDecisionRequirementsSearchRequest()
            .send()
            .join()
            .items()
            .getFirst()
            .getDecisionRequirementsKey();
  }

  @ParameterizedTest
  @MethodSource("getRequestParameters")
  void shouldEvaluateGetRequest(
      final String user, final String endpoint, final int expectedStatus, final String key)
      throws Exception {
    try (final var client = STANDALONE_CAMUNDA.newOperateClient(user, user)) {
      final int statusCode = client.sendGetRequest(endpoint, key).statusCode();
      assertThat(statusCode).isEqualTo(expectedStatus);
    }
  }

  @ParameterizedTest
  @MethodSource("searchRequestParameters")
  void shouldEvaluateSearchRequest(
      final String user, final String endpoint, final int expectedStatus) throws Exception {
    try (final var client = STANDALONE_CAMUNDA.newOperateClient(user, user)) {
      final int statusCode = client.sendV1SearchRequest(endpoint, "{}").statusCode();
      assertThat(statusCode).isEqualTo(expectedStatus);
    }
  }

  @ParameterizedTest
  @MethodSource({"deleteRequestParameters"})
  void shouldEvaluateDeleteRequest(
      final String user, final int expectedStatus, final CamundaClient adminClient)
      throws Exception {
    // start instance for deletion
    final long processInstanceToDeleteKey =
        startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToStart(
        adminClient, f -> f.processInstanceKey(processInstanceToDeleteKey), 1);
    adminClient.newCancelInstanceCommand(processInstanceToDeleteKey).send().join();
    waitForProcessInstanceToBeTerminated(adminClient, processInstanceToDeleteKey);

    try (final var client = STANDALONE_CAMUNDA.newOperateClient(user, user)) {
      final int statusCode =
          client.sendDeleteRequest("v1/process-instances", processInstanceToDeleteKey).statusCode();
      assertThat(statusCode).isEqualTo(expectedStatus);
    }
  }

  private static Stream<Arguments> getRequestParameters() {
    return Stream.of(
        // Process Instances
        Arguments.of(
            AUTHORIZED_USERNAME,
            PROCESS_INSTANCE_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processInstanceKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            PROCESS_INSTANCE_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processInstanceKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            PROCESS_INSTANCE_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processInstanceKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            PROCESS_INSTANCE_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(processInstanceKey)),
        // Process Definitions
        Arguments.of(
            AUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            AUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(processDefinitionKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            PROCESS_DEFINITION_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(processDefinitionKey)),
        // Flownode Instances
        Arguments.of(
            AUTHORIZED_USERNAME,
            FLOWNODE_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(flowNodeInstanceKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            FLOWNODE_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(flowNodeInstanceKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            FLOWNODE_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(flowNodeInstanceKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            FLOWNODE_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(flowNodeInstanceKey)),
        // Incident
        Arguments.of(
            AUTHORIZED_USERNAME,
            INCIDENT_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(incidentKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            INCIDENT_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(incidentKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            INCIDENT_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(incidentKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            INCIDENT_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(incidentKey)),
        // Variables
        Arguments.of(
            AUTHORIZED_USERNAME,
            VARIABLES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(variableKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            VARIABLES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(variableKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            VARIABLES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(variableKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            VARIABLES_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(variableKey)),
        // Decision Definitions
        Arguments.of(
            AUTHORIZED_USERNAME,
            DECISION_DEFINITIONS_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionDefinitionKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            DECISION_DEFINITIONS_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionDefinitionKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            DECISION_DEFINITIONS_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionDefinitionKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            DECISION_DEFINITIONS_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(decisionDefinitionKey)),
        // Decision Instances
        Arguments.of(
            AUTHORIZED_USERNAME,
            DECISION_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionInstanceId)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            DECISION_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionInstanceId)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            DECISION_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionInstanceId)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            DECISION_INSTANCES_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(decisionInstanceId)),
        // Decision Requirements
        Arguments.of(
            AUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            AUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.OK.value(),
            String.valueOf(decisionRequirementsKey)),
        Arguments.of(
            UNAUTHORIZED_USERNAME,
            DECISION_REQUIREMENTS_GET_XML_ENDPOINT_PATTERN,
            HttpStatus.FORBIDDEN.value(),
            String.valueOf(decisionRequirementsKey)));
  }

  private static Stream<Arguments> searchRequestParameters() {
    return Stream.of(
        // Process Instances
        Arguments.of(AUTHORIZED_USERNAME, PROCESS_INSTANCE_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, PROCESS_INSTANCE_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, PROCESS_INSTANCE_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            UNAUTHORIZED_USERNAME, PROCESS_INSTANCE_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Process Definitions
        Arguments.of(AUTHORIZED_USERNAME, PROCESS_DEFINITION_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, PROCESS_DEFINITION_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, PROCESS_DEFINITION_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            UNAUTHORIZED_USERNAME, PROCESS_DEFINITION_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Flownode Instances
        Arguments.of(AUTHORIZED_USERNAME, FLOWNODE_INSTANCES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, FLOWNODE_INSTANCES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, FLOWNODE_INSTANCES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            UNAUTHORIZED_USERNAME, FLOWNODE_INSTANCES_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Incidents
        Arguments.of(AUTHORIZED_USERNAME, INCIDENT_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, INCIDENT_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, INCIDENT_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(UNAUTHORIZED_USERNAME, INCIDENT_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Variables
        Arguments.of(AUTHORIZED_USERNAME, VARIABLES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, VARIABLES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, VARIABLES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(UNAUTHORIZED_USERNAME, VARIABLES_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Decision Definitions
        Arguments.of(AUTHORIZED_USERNAME, DECISION_DEFINITIONS_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME, DECISION_DEFINITIONS_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME, DECISION_DEFINITIONS_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            UNAUTHORIZED_USERNAME, DECISION_DEFINITIONS_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Decision Instances
        Arguments.of(AUTHORIZED_USERNAME, DECISION_INSTANCES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, DECISION_INSTANCES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, DECISION_INSTANCES_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            UNAUTHORIZED_USERNAME, DECISION_INSTANCES_ENDPOINT, HttpStatus.FORBIDDEN.value()),
        // Decision Requirements
        Arguments.of(AUTHORIZED_USERNAME, DECISION_REQUIREMENTS_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            ROLE_AUTHORIZED_USERNAME, DECISION_REQUIREMENTS_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            GROUP_AUTHORIZED_USERNAME, DECISION_REQUIREMENTS_ENDPOINT, HttpStatus.OK.value()),
        Arguments.of(
            UNAUTHORIZED_USERNAME, DECISION_REQUIREMENTS_ENDPOINT, HttpStatus.FORBIDDEN.value()));
  }

  private static Stream<Arguments> deleteRequestParameters() {
    return Stream.of(
        /* depends on fix for https://github.com/camunda/camunda/issues/36067
        Arguments.of(AUTHORIZED_USERNAME, HttpStatus.OK.value()),
        Arguments.of(ROLE_AUTHORIZED_USERNAME, HttpStatus.OK.value()),
        Arguments.of(GROUP_AUTHORIZED_USERNAME, HttpStatus.OK.value()),
        */
        Arguments.of(UNAUTHORIZED_USERNAME, HttpStatus.FORBIDDEN.value()));
  }
}
