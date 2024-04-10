/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class VariablesControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
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
