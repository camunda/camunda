/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class VariablesControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @Before
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void getTaskVariableById() throws Exception {
    // given
    final var bpmnProcessId = "simpleTestProcess";
    final var flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var variables =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 1)
            .and()
            .taskIsCreated(flowNodeBpmnId)
            .and()
            .claimAndCompleteHumanTask(flowNodeBpmnId, "freezingPointF", "32")
            .then()
            .getTaskVariables();
    assertThat(variables).hasSize(1);

    final var variableId = variables.get(0).getId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), variableId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, VariableResponse.class)
        .satisfies(
            var -> {
              assertThat(var.getId()).isEqualTo(variableId);
              assertThat(var.getName()).isEqualTo("freezingPointF");
              assertThat(var.getValue()).isEqualTo("32");
            });
  }

  @Test
  public void getVariableWithDraftValueById() throws Exception {
    // given
    final var bpmnProcessId = "simpleTestProcess";
    final var flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var taskTester =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, "{\"intVar\": 111}")
            .and()
            .taskIsCreated(flowNodeBpmnId)
            .and()
            .claimHumanTask(flowNodeBpmnId);
    final var taskId = taskTester.getTaskId();
    final var variables = taskTester.getTaskVariables();
    final var variableId = variables.get(0).getId();
    assertThat(variables).hasSize(1);

    final var saveVariablesRequest =
        new SaveVariablesRequest()
            .setVariables(List.of(new VariableInputDTO().setName("intVar").setValue("222")));

    // when
    final var persistDraftVariablesResult =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
            saveVariablesRequest);
    // then
    assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), variableId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, VariableResponse.class)
        .satisfies(
            var -> {
              assertThat(var.getId()).isEqualTo(variableId);
              assertThat(var.getName()).isEqualTo("intVar");
              assertThat(var.getValue()).isEqualTo("111");
              assertThat(var.getDraft())
                  .isEqualTo(new VariableResponse.DraftVariableValue().setValue("222"));
            });
  }

  @Test
  public void getDraftVariableById() throws Exception {
    // given
    final var bpmnProcessId = "simpleTestProcess";
    final var flowNodeBpmnId = "taskH_".concat(UUID.randomUUID().toString());
    final var taskTester =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, "{\"intVar\": 111}")
            .and()
            .taskIsCreated(flowNodeBpmnId)
            .and()
            .claimHumanTask(flowNodeBpmnId);
    final var taskId = taskTester.getTaskId();
    final var variables = taskTester.getTaskVariables();
    assertThat(variables).hasSize(1);

    final var saveVariablesRequest =
        new SaveVariablesRequest()
            .setVariables(
                List.of(new VariableInputDTO().setName("strVar").setValue("\"someString\"")));

    // when
    final var persistDraftVariablesResult =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId),
            saveVariablesRequest);
    // then
    assertThat(persistDraftVariablesResult).hasHttpStatus(HttpStatus.NO_CONTENT);

    // when
    final var strVarVariableId = taskId.concat("-strVar");
    final var result =
        mockMvcHelper.doRequest(
            get(TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), strVarVariableId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, VariableResponse.class)
        .satisfies(
            var -> {
              assertThat(var.getId()).isEqualTo(strVarVariableId);
              assertThat(var.getName()).isEqualTo("strVar");
              assertThat(var.getValue()).isNull();
              assertThat(var.getDraft())
                  .isEqualTo(new VariableResponse.DraftVariableValue().setValue("\"someString\""));
            });
  }

  @Test
  public void getVariableByIdWhenVariableNotExistThen404ErrorExpected() {
    // given
    final var variableId = "not-found-445";

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            get(TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), variableId));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("Variable with id %s not found.", variableId);
  }
}
