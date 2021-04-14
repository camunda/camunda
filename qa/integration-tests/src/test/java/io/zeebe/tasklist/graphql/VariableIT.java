/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import io.zeebe.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class VariableIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String GET_TASK_QUERY_PATTERN =
      "{task(id: \"%s\"){id variables {name value}}}";

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Before
  public void before() {
    super.before();
    taskMutationResolver.setZeebeClient(super.getClient());
  }

  @Test
  public void shouldReturnOverwrittenVariable() throws IOException {
    // having
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeInput("=5", "overwrittenVar")
            .zeebeInput("=upperLevelVar*2", "innerVar")
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .deployProcess(process, bpmnProcessId + ".bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(
                bpmnProcessId, "{\"upperLevelVar\": 1, \"overwrittenVar\": \"10\"}")
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .startProcessInstance(bpmnProcessId, "{\"upperLevelVar\": 2}")
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));

    // alphabetic sorting is also checked here
    assertEquals("3", response.get("$.data.tasks[0].variables.length()"));
    assertEquals("innerVar", response.get("$.data.tasks[0].variables[0].name"));
    assertEquals("4", response.get("$.data.tasks[0].variables[0].value"));
    assertEquals("overwrittenVar", response.get("$.data.tasks[0].variables[1].name"));
    assertEquals("5", response.get("$.data.tasks[0].variables[1].value"));
    assertEquals("upperLevelVar", response.get("$.data.tasks[0].variables[2].name"));
    assertEquals("2", response.get("$.data.tasks[0].variables[2].value"));

    assertEquals("3", response.get("$.data.tasks[1].variables.length()"));
    assertEquals("innerVar", response.get("$.data.tasks[1].variables[0].name"));
    assertEquals("2", response.get("$.data.tasks[1].variables[0].value"));
    assertEquals("overwrittenVar", response.get("$.data.tasks[1].variables[1].name"));
    assertEquals("5", response.get("$.data.tasks[1].variables[1].value"));
    assertEquals("upperLevelVar", response.get("$.data.tasks[1].variables[2].name"));
    assertEquals("1", response.get("$.data.tasks[1].variables[2].value"));
  }

  @Test
  public void shouldReturnSubprocessVariable() throws IOException {
    // having
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent("start")
            .subProcess()
            .zeebeInput("=222", "subprocessVar")
            .embeddedSubProcess()
            .startEvent()
            .userTask(flowNodeBpmnId)
            .zeebeInput("=333", "taskVar")
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .deployProcess(process, bpmnProcessId + ".bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, "{\"processVar\": 111}")
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));

    assertEquals("3", response.get("$.data.tasks[0].variables.length()"));
    assertEquals("processVar", response.get("$.data.tasks[0].variables[0].name"));
    assertEquals("111", response.get("$.data.tasks[0].variables[0].value"));
    assertEquals("subprocessVar", response.get("$.data.tasks[0].variables[1].name"));
    assertEquals("222", response.get("$.data.tasks[0].variables[1].value"));
    assertEquals("taskVar", response.get("$.data.tasks[0].variables[2].name"));
    assertEquals("333", response.get("$.data.tasks[0].variables[2].value"));
  }

  @Test
  public void shouldReturnMultiInstanceVariables() throws IOException {
    // having
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeInput("=333", "taskVar")
            .multiInstance()
            .sequential()
            .zeebeInputCollection("=clients")
            .zeebeInputElement("client")
            .zeebeOutputCollection("results")
            .zeebeOutputElement("=result")
            .multiInstanceDone()
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .deployProcess(process, bpmnProcessId + ".bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, "{\"processVar\": 111, \"clients\": [1, 2]}")
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .and()
            .claimAndCompleteHumanTask(flowNodeBpmnId, "result", "\"SUCCESS\"")
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));

    for (int i = 0; i < 2; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertThat(response.get(taskJsonPath + ".variables.length()"))
          .as("Task %d variables count", i)
          .isEqualTo("7");
      assertThat(Arrays.asList(response.get(taskJsonPath + ".variables", Object[].class)))
          .extracting(o -> ((Map) o).get("name"))
          .isSorted()
          .containsExactlyInAnyOrder(
              "processVar", "clients", "client", "loopCounter", "taskVar", "result", "results");
    }
  }

  @Test
  public void shouldReturnOneTaskWithVariables() throws IOException {
    // having
    final GraphQLResponse response =
        tester
            .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, "{\"var\": 111}")
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .and()
            .getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse =
        graphQLTestTemplate.postMultipart(String.format(GET_TASK_QUERY_PATTERN, taskId), "{}");

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals("1", taskResponse.get("$.data.task.variables.length()"));
    assertEquals("var", taskResponse.get("$.data.task.variables[0].name"));
    assertEquals("111", taskResponse.get("$.data.task.variables[0].value"));
  }

  @Test
  public void shouldReturnEventSubprocessVariable() throws IOException {
    // having
    final GraphQLResponse response =
        tester
            .deployProcess("eventSubProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance("eventSubprocessProcess", "{\"processVar\": 111}")
            .waitUntil()
            .taskIsCreated("subProcessTask")
            .and()
            .getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse =
        graphQLTestTemplate.postMultipart(String.format(GET_TASK_QUERY_PATTERN, taskId), "{}");

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals("2", taskResponse.get("$.data.task.variables.length()"));
    assertEquals("processVar", taskResponse.get("$.data.task.variables[0].name"));
    assertEquals("111", taskResponse.get("$.data.task.variables[0].value"));
    assertEquals("subprocessVar", taskResponse.get("$.data.task.variables[1].name"));
    assertEquals("111", taskResponse.get("$.data.task.variables[1].value"));
  }
}
