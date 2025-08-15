/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilAuthorizationVisible;
import static io.camunda.qa.util.cluster.TestRestOperateClient.toJsonString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.it.util.TestHelper;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateInternalApiPermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static final String SUPER_USER_USERNAME = "super";
  private static final String RESTRICTED_USER_USERNAME = "restricted";
  private static final String PROCESS_DEFINITION_ID_1 = "service_tasks_v1";
  private static final String PROCESS_DEFINITION_ID_2 = "incident_process_v1";
  private static Decision decisionToFind;
  private static Decision decisionNotToFind;
  private static Process processToFind;
  private static Process processNotToFind;
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  @UserDefinition
  private static final TestUser SUPER_USER =
      new TestUser(
          SUPER_USER_USERNAME,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of("*")),
              new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*")),
              new Permissions(DECISION_REQUIREMENTS_DEFINITION, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED_USER_USERNAME,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_DEFINITION_ID_1))));

  @BeforeAll
  public static void beforeAll(final CamundaClient adminClient) throws Exception {
    final List<String> processes = List.of(PROCESS_DEFINITION_ID_1, PROCESS_DEFINITION_ID_2);
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(adminClient, String.format("process/%s.bpmn", process))
                    .getProcesses()));
    assertThat(DEPLOYED_PROCESSES).hasSize(processes.size());

    waitForProcessesToBeDeployed(adminClient, DEPLOYED_PROCESSES.size());

    // DMN
    decisionToFind =
        TestHelper.deployDefaultTestDecisionProcessInstance(adminClient, "decision_model.dmn");
    decisionNotToFind =
        TestHelper.deployDefaultTestDecisionProcessInstance(adminClient, "decision_model_1.dmn");
    processToFind =
        TestHelper.startDefaultTestDecisionProcessInstance(
            adminClient, decisionToFind.getDmnDecisionId(), "dmnProcess1");
    processNotToFind =
        TestHelper.startDefaultTestDecisionProcessInstance(
            adminClient, decisionNotToFind.getDmnDecisionId(), "dmnProcess2");

    // give restricted user access to one of the decisions
    adminClient
        .newCreateAuthorizationCommand()
        .ownerId(RESTRICTED_USER_USERNAME)
        .ownerType(OwnerType.USER)
        .resourceId(decisionToFind.getDmnDecisionId())
        .resourceType(DECISION_DEFINITION)
        .permissionTypes(READ_DECISION_INSTANCE, READ_DECISION_DEFINITION)
        .send()
        .join();
    adminClient
        .newCreateAuthorizationCommand()
        .ownerId(RESTRICTED_USER_USERNAME)
        .ownerType(OwnerType.USER)
        .resourceId(processToFind.getBpmnProcessId())
        .resourceType(PROCESS_DEFINITION)
        .permissionTypes(READ_PROCESS_INSTANCE)
        .send()
        .join();
    waitUntilAuthorizationVisible(
        adminClient, RESTRICTED_USER_USERNAME, decisionToFind.getDmnDecisionId());
    waitUntilAuthorizationVisible(
        adminClient, RESTRICTED_USER_USERNAME, processToFind.getBpmnProcessId());
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  public void shouldGetProcessByKeyOnlyForAuthorizedProcesses() {
    // super user can read all process definitions
    final var operateClient =
        STANDALONE_CAMUNDA.newOperateClient(SUPER_USER.username(), SUPER_USER.password());

    assertThat(
            DEPLOYED_PROCESSES.stream()
                .map(Process::getProcessDefinitionKey)
                .allMatch(
                    (key) ->
                        operateClient.internalGetProcessDefinitionByKey(key).get().statusCode()
                            == 200))
        .isTrue();

    // restricted user can read process definition 1
    final var restrictedOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(RESTRICTED_USER.username(), RESTRICTED_USER.password());

    assertThat(
            restrictedOperateClient
                .internalGetProcessDefinitionByKey(
                    DEPLOYED_PROCESSES.get(0).getProcessDefinitionKey())
                .get()
                .statusCode())
        .isEqualTo(200);

    // restricted user cannot read process definition 2
    assertThat(
            restrictedOperateClient
                .internalGetProcessDefinitionByKey(
                    DEPLOYED_PROCESSES.get(1).getProcessDefinitionKey())
                .get()
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  public void shouldReturnOnlyAuthorizedDecisionInstances(final CamundaClient adminClient)
      throws URISyntaxException, IOException, InterruptedException {
    // given
    final String decisionInstanceRequest =
        toJsonString(
            new DecisionInstanceListRequestDto()
                .setQuery(new DecisionInstanceListQueryDto().setFailed(true).setEvaluated(true)));

    final var adminOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(SUPER_USER_USERNAME, SUPER_USER.password());
    final var restrictedOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(RESTRICTED_USER_USERNAME, RESTRICTED_USER.password());

    // when
    final var adminResponse =
        adminOperateClient.sendInternalSearchRequest(
            "api/decision-instances", decisionInstanceRequest);
    final var adminResponseDto =
        adminOperateClient.mapResult(adminResponse, DecisionInstanceListResponseDto.class);

    final var restrictedResponse =
        restrictedOperateClient.sendInternalSearchRequest(
            "api/decision-instances", decisionInstanceRequest);
    final var restrictedResponseDto =
        restrictedOperateClient.mapResult(
            restrictedResponse, DecisionInstanceListResponseDto.class);

    // then
    assertThat(adminResponseDto.isRight()).isTrue();
    assertThat(adminResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(((DecisionInstanceListResponseDto) adminResponseDto.get()).getTotalCount())
        .isEqualTo(2);

    assertThat(restrictedResponseDto.isRight()).isTrue();
    final DecisionInstanceListResponseDto restrictedDto =
        (DecisionInstanceListResponseDto) restrictedResponseDto.get();
    assertThat(restrictedResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(restrictedDto.getTotalCount()).isOne();
    assertThat(restrictedDto.getDecisionInstances().getFirst().getDecisionName())
        .isEqualTo(decisionToFind.getDmnDecisionName());
  }

  @Test
  public void shouldReturnOnlyAuthorizedGroupedDecisionDefinitions(final CamundaClient adminClient)
      throws URISyntaxException, IOException, InterruptedException {
    // given
    final String decisionInstanceRequest = toJsonString(new DecisionRequestDto());
    final var restrictedOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(RESTRICTED_USER_USERNAME, RESTRICTED_USER.password());

    // when
    final var restrictedResponse =
        restrictedOperateClient.sendInternalSearchRequest(
            "api/decisions/grouped", decisionInstanceRequest);
    final var restrictedResponseDto =
        restrictedOperateClient.mapResult(restrictedResponse, DecisionGroupDto[].class);

    // then
    assertThat(restrictedResponseDto.isRight()).isTrue();
    final DecisionGroupDto[] restrictedDto = (DecisionGroupDto[]) restrictedResponseDto.get();
    assertThat(restrictedResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(restrictedDto).hasSize(1);
    assertThat(restrictedDto[0].getDecisionId()).isEqualTo(decisionToFind.getDmnDecisionId());
  }

  @Test
  public void shouldReturnAllAuthorizedGroupedDecisionDefinitions(final CamundaClient adminClient)
      throws URISyntaxException, IOException, InterruptedException {
    // given
    final String decisionInstanceRequest = toJsonString(new DecisionRequestDto());
    final var adminOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(SUPER_USER_USERNAME, SUPER_USER.password());

    // when
    final var adminResponse =
        adminOperateClient.sendInternalSearchRequest(
            "api/decisions/grouped", decisionInstanceRequest);
    final var adminResponseDto =
        adminOperateClient.mapResult(adminResponse, DecisionGroupDto[].class);

    // then
    assertThat(adminResponseDto.isRight()).isTrue();
    assertThat(adminResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(((DecisionGroupDto[]) adminResponseDto.get())).hasSize(2);
  }

  @Test
  public void shouldReturnAuthorizedFlowNodeStatistics(final CamundaClient adminClient)
      throws URISyntaxException, IOException, InterruptedException {
    // given
    final String flowNodeStatisticsDto =
        toJsonString(
            new ListViewQueryDto()
                .setActive(true)
                .setCompleted(true)
                .setRunning(true)
                .setFinished(true)
                .setProcessIds(List.of(String.valueOf(processToFind.getProcessDefinitionKey()))));
    final var restrictedOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(RESTRICTED_USER_USERNAME, RESTRICTED_USER.password());

    // when
    final var restrictedResponse =
        restrictedOperateClient.sendInternalSearchRequest(
            "api/process-instances/statistics", flowNodeStatisticsDto);
    final var restrictedResponseMapping =
        restrictedOperateClient.mapResult(restrictedResponse, FlowNodeStatisticsDto[].class);

    // then
    assertThat(restrictedResponseMapping.isRight()).isTrue();
    final var restrictedResponseDto = (FlowNodeStatisticsDto[]) restrictedResponseMapping.get();
    assertThat(restrictedResponseDto).hasSize(1);
  }

  @Test
  public void shouldNotReturnUnauthorizedFlowNodeStatistics(final CamundaClient adminClient)
      throws URISyntaxException, IOException, InterruptedException {
    final String flowNodeStatisticsDto =
        toJsonString(
            new ListViewQueryDto()
                .setActive(true)
                .setCompleted(true)
                .setRunning(true)
                .setFinished(true)
                .setProcessIds(
                    List.of(String.valueOf(processNotToFind.getProcessDefinitionKey()))));

    final var restrictedOperateClient =
        STANDALONE_CAMUNDA.newOperateClient(RESTRICTED_USER_USERNAME, RESTRICTED_USER.password());

    final var restrictedResponse =
        restrictedOperateClient.sendInternalSearchRequest(
            "api/process-instances/statistics", flowNodeStatisticsDto);
    final var restrictedResponseMapping =
        restrictedOperateClient.mapResult(restrictedResponse, FlowNodeStatisticsDto[].class);
    assertThat(restrictedResponseMapping.isRight()).isTrue();
    final var restrictedResponseDto = (FlowNodeStatisticsDto[]) restrictedResponseMapping.get();
    assertThat(restrictedResponseDto).isEmpty();
  }
}
