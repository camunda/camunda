/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.qa.util.VariablesUtil.createBigVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import com.jayway.jsonpath.PathNotFoundException;
import io.camunda.tasklist.util.SpringParametersRunnerFactory;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@UseParametersRunnerFactory(SpringParametersRunnerFactory.class)
@RunWith(Parameterized.class)
public class VariableIT extends TasklistZeebeIntegrationTest {

  private static final String ELEMENT_ID = "taskA";
  private static final String BPMN_PROCESS_ID = "testProcess";

  @Parameter(value = 0)
  public String variableFragmentResource;

  @Parameter(value = 1)
  public boolean shouldContainPreview;

  @Parameter(value = 2)
  public boolean shouldContainFullValue;

  @Parameter(value = 3)
  public boolean shouldContainIsTruncatedFlag;

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private ObjectMapper objectMapper;

  @Parameters
  public static Collection<Object[]> data() {
    final Collection<Object[]> params = new ArrayList();
    params.add(
        new Object[] {"graphql/variableIT/full-variable-fragment.graphql", true, true, true});
    params.add(
        new Object[] {"graphql/variableIT/partial-variable-fragment1.graphql", true, false, true});
    params.add(
        new Object[] {"graphql/variableIT/partial-variable-fragment2.graphql", false, true, false});
    return params;
  }

  @Before
  public void before() {
    super.before();
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
            .and()
            .variablesExist(new String[] {"innerVar", "overwrittenVar", "upperLevelVar"})
            .when()
            .getAllTasks(variableFragmentResource);

    // then
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));

    // alphabetic sorting is also checked here
    assertEquals("3", response.get("$.data.tasks[0].variables.length()"));
    assertEquals("innerVar", response.get("$.data.tasks[0].variables[0].name"));
    assertEquals("overwrittenVar", response.get("$.data.tasks[0].variables[1].name"));
    assertEquals("upperLevelVar", response.get("$.data.tasks[0].variables[2].name"));
    assertEqualsWithExistenceCheck(
        "4", () -> response.get("$.data.tasks[0].variables[0].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "5", () -> response.get("$.data.tasks[0].variables[1].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "2", () -> response.get("$.data.tasks[0].variables[2].value"), shouldContainFullValue);

    assertEquals("3", response.get("$.data.tasks[1].variables.length()"));
    assertEquals("innerVar", response.get("$.data.tasks[1].variables[0].name"));
    assertEquals("overwrittenVar", response.get("$.data.tasks[1].variables[1].name"));
    assertEquals("upperLevelVar", response.get("$.data.tasks[1].variables[2].name"));
    assertEqualsWithExistenceCheck(
        "2", () -> response.get("$.data.tasks[1].variables[0].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "5", () -> response.get("$.data.tasks[1].variables[1].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "1", () -> response.get("$.data.tasks[1].variables[2].value"), shouldContainFullValue);

    IntStream.range(0, 1)
        .forEach(
            i ->
                IntStream.range(0, 2)
                    .forEach(
                        j -> {
                          assertEqualsWithExistenceCheck(
                              "false",
                              () ->
                                  response.get(
                                      "$.data.tasks["
                                          + i
                                          + "].variables["
                                          + j
                                          + "].isValueTruncated"),
                              shouldContainIsTruncatedFlag);
                          if (shouldContainPreview && shouldContainFullValue) {
                            assertEquals(
                                response.get(
                                    "$.data.tasks[" + i + "].variables[" + j + "].previewValue"),
                                response.get("$.data.tasks[" + i + "].variables[" + j + "].value"));
                          } else if (!shouldContainPreview) {
                            assertThatExceptionOfType(PathNotFoundException.class)
                                .isThrownBy(
                                    () ->
                                        response.get(
                                            "$.data.tasks["
                                                + i
                                                + "].variables["
                                                + j
                                                + "].previewValue"));
                          }
                        }));
  }

  private void assertEqualsWithExistenceCheck(
      Object expected, Supplier<Object> getActualValue, boolean shouldExist) {
    if (shouldExist) {
      assertEquals(expected, getActualValue.get());
    } else {
      assertThatExceptionOfType(PathNotFoundException.class).isThrownBy(() -> getActualValue.get());
    }
  }

  @Test
  public void shouldReturnOverwrittenBigVariablesWithPreview() throws IOException {
    // having
    final int size =
        tasklistProperties.getImporter().getVariableSizeThreshold()
            - 1; // -1 as we work with string and there is a quote at the beginning
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final String suffix = "999";
    final String overwrittenVarValue1 = createBigVariable(size);
    final String overwrittenVarValue2 = createBigVariable(size);
    final String upperLevelVarValue = createBigVariable(size);

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeInput("=\"" + overwrittenVarValue2 + suffix + "\"", "overwrittenVar")
            .endEvent()
            .done();

    final GraphQLResponse response =
        tester
            .deployProcess(process, bpmnProcessId + ".bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(
                bpmnProcessId,
                "{\"upperLevelVar\": \""
                    + upperLevelVarValue
                    + suffix
                    + "\", \"overwrittenVar\": \""
                    + overwrittenVarValue1
                    + suffix
                    + "\"}")
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .when()
            .getAllTasks(variableFragmentResource);

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));

    assertEquals("2", response.get("$.data.tasks[0].variables.length()"));
    assertEquals("overwrittenVar", response.get("$.data.tasks[0].variables[0].name"));
    assertEqualsWithExistenceCheck(
        "\"" + overwrittenVarValue2 + suffix + "\"",
        () -> response.get("$.data.tasks[0].variables[0].value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + overwrittenVarValue2 + "",
        () -> response.get("$.data.tasks[0].variables[0].previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> response.get("$.data.tasks[0].variables[0].isValueTruncated"),
        shouldContainIsTruncatedFlag);

    assertEquals("upperLevelVar", response.get("$.data.tasks[0].variables[1].name"));
    assertEqualsWithExistenceCheck(
        "\"" + upperLevelVarValue + suffix + "\"",
        () -> response.get("$.data.tasks[0].variables[1].value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + upperLevelVarValue + "",
        () -> response.get("$.data.tasks[0].variables[1].previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> response.get("$.data.tasks[0].variables[1].isValueTruncated"),
        shouldContainIsTruncatedFlag);

    // get one variable by id
    // when
    final String variableId = response.get("$.data.tasks[0].variables[0].id");
    final GraphQLResponse varResponse =
        tester.getVariableById(variableId, variableFragmentResource);

    // then
    assertEquals(variableId, varResponse.get("$.data.variable.id"));
    assertEquals("overwrittenVar", varResponse.get("$.data.variable.name"));
    assertEqualsWithExistenceCheck(
        "\"" + overwrittenVarValue2 + suffix + "\"",
        () -> varResponse.get("$.data.variable.value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + overwrittenVarValue2 + "",
        () -> varResponse.get("$.data.variable.previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> varResponse.get("$.data.variable.isValueTruncated"),
        shouldContainIsTruncatedFlag);
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
    final GraphQLResponse taskResponse = tester.getTaskById(taskId, variableFragmentResource);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals("1", taskResponse.get("$.data.task.variables.length()"));
    assertEquals("var", taskResponse.get("$.data.task.variables[0].name"));
    assertEqualsWithExistenceCheck(
        "111", () -> taskResponse.get("$.data.task.variables[0].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "111",
        () -> taskResponse.get("$.data.task.variables[0].previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "false",
        () -> taskResponse.get("$.data.task.variables[0].isValueTruncated"),
        shouldContainIsTruncatedFlag);
  }

  @Test
  public void shouldUpdateVariables() throws IOException {
    final String taskAId = "taskA";
    final String varName = "var";
    final String var2Name = "var2";
    final String var3Name = "var3";

    final int size =
        tasklistProperties.getImporter().getVariableSizeThreshold()
            - 1; // -1 as we work with string and there is a quote at the beginning
    final String suffix = "999";
    final String varValue = createBigVariable(size);

    // having
    final GraphQLResponse response =
        tester
            .deployProcess("simple_process_2.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(
                "testProcess2", "{\"" + varName + "\": 111, \"" + var2Name + "\": \"value\"}")
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .variablesExist(new String[] {varName})
            .and()
            .claimHumanTask(taskAId)
            .and()
            .completeHumanTask(
                taskAId, varName, "222", var2Name, "\"" + varValue + suffix + "\"", var3Name, "111")
            .waitUntil()
            .variablesExist(new String[] {var3Name})
            .when()
            .getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId, variableFragmentResource);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals("3", taskResponse.get("$.data.task.variables.length()"));
    for (int i = 0; i < 2; i++) {
      if (taskResponse.get("$.data.task.variables[" + i + "].name").equals(varName)) {
        final int finalI = i;
        assertEqualsWithExistenceCheck(
            "222",
            () -> taskResponse.get("$.data.task.variables[" + finalI + "].value"),
            shouldContainFullValue);
        assertEqualsWithExistenceCheck(
            "222",
            () -> taskResponse.get("$.data.task.variables[" + finalI + "].previewValue"),
            shouldContainPreview);
        assertEqualsWithExistenceCheck(
            "false",
            () -> taskResponse.get("$.data.task.variables[" + finalI + "].isValueTruncated"),
            shouldContainIsTruncatedFlag);
      } else if (taskResponse.get("$.data.task.variables[" + i + "].name").equals(var2Name)) {
        final int finalI = i;
        assertEqualsWithExistenceCheck(
            "\"" + varValue + suffix + "\"",
            () -> taskResponse.get("$.data.task.variables[" + finalI + "].value"),
            shouldContainFullValue);
        assertEqualsWithExistenceCheck(
            "\"" + varValue + "",
            () -> taskResponse.get("$.data.task.variables[" + finalI + "].previewValue"),
            shouldContainPreview);
        assertEqualsWithExistenceCheck(
            "true",
            () -> taskResponse.get("$.data.task.variables[" + finalI + "].isValueTruncated"),
            shouldContainIsTruncatedFlag);
      } else if (!taskResponse.get("$.data.task.variables[" + i + "].name").equals(var3Name)) {
        fail(
            String.format(
                "Variable with name %s is not expected",
                taskResponse.get("$.data.task.variables[" + i + "].name")));
      }
    }
  }

  @Test
  public void shouldReturnOneTaskWithBigVariablesWithPreview() throws IOException {
    // having
    final int size =
        tasklistProperties.getImporter().getVariableSizeThreshold()
            - 1; // -1 as we work with string and there is a quote at the beginning

    final String suffix = "999";
    final String varValue = createBigVariable(size);

    final GraphQLResponse response =
        tester
            .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, "{\"var\": \"" + varValue + suffix + "\"}")
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .and()
            .getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId, variableFragmentResource);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    final String variableId = taskResponse.get("$.data.task.variables[0].id");
    assertNotNull(variableId);
    assertEquals("1", taskResponse.get("$.data.task.variables.length()"));
    assertEquals("var", taskResponse.get("$.data.task.variables[0].name"));
    assertEqualsWithExistenceCheck(
        "\"" + varValue + suffix + "\"",
        () -> taskResponse.get("$.data.task.variables[0].value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + varValue + "",
        () -> taskResponse.get("$.data.task.variables[0].previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> taskResponse.get("$.data.task.variables[0].isValueTruncated"),
        shouldContainIsTruncatedFlag);

    // get one variable by id
    // when
    final GraphQLResponse varResponse =
        tester.getVariableById(variableId, variableFragmentResource);

    // then
    assertEquals(variableId, varResponse.get("$.data.variable.id"));
    assertEquals("var", varResponse.get("$.data.variable.name"));
    assertEqualsWithExistenceCheck(
        "\"" + varValue + suffix + "\"",
        () -> varResponse.get("$.data.variable.value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + varValue + "",
        () -> varResponse.get("$.data.variable.previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> varResponse.get("$.data.variable.isValueTruncated"),
        shouldContainIsTruncatedFlag);
  }

  @Test
  public void shouldReturnVariablesByNames() throws IOException {
    // having
    final String taskId =
        tester
            .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, "{\"var1\": 111, \"var2\": 222, \"var3\": 333}")
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .and()
            .getTaskId();

    // when
    final ObjectNode variablesQ = objectMapper.createObjectNode();
    variablesQ.put("taskId", taskId).putArray("variableNames").add("var1").add("var3");
    final GraphQLResponse response =
        tester.getVariablesByTaskIdAndNames(variablesQ, variableFragmentResource);

    // then
    assertThat(response.get("$.data.variables.length()")).isEqualTo("2");
    assertThat(Arrays.asList(response.get("$.data.variables", Object[].class)))
        .extracting(o -> ((Map) o).get("name"))
        .containsExactly("var1", "var3");
    assertEqualsWithExistenceCheck(
        "111", () -> response.get("$.data.variables[0].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "333", () -> response.get("$.data.variables[1].value"), shouldContainFullValue);
  }

  @Test
  public void shouldReturnVariablesByNamesForCompletedTask() throws IOException {
    // having
    final String taskId =
        tester
            .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, "{\"var1\": 111, \"var2\": 222, \"var3\": 333}")
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .claimHumanTask(ELEMENT_ID)
            .and()
            .completeHumanTask(ELEMENT_ID)
            .waitUntil()
            .taskVariableExists("var1")
            .getTaskId();

    // when
    final ObjectNode variablesQ = objectMapper.createObjectNode();
    variablesQ.put("taskId", taskId).putArray("variableNames").add("var1").add("var3");
    final GraphQLResponse response =
        tester.getVariablesByTaskIdAndNames(variablesQ, variableFragmentResource);

    // then
    assertThat(response.get("$.data.variables.length()")).isEqualTo("2");
    assertThat(Arrays.asList(response.get("$.data.variables", Object[].class)))
        .extracting(o -> ((Map) o).get("name"))
        .isSorted()
        .containsExactlyInAnyOrder("var1", "var3");
    assertEqualsWithExistenceCheck(
        "111", () -> response.get("$.data.variables[0].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "333", () -> response.get("$.data.variables[1].value"), shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "111", () -> response.get("$.data.variables[0].previewValue"), shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "333", () -> response.get("$.data.variables[1].previewValue"), shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "false",
        () -> response.get("$.data.variables[0].isValueTruncated"),
        shouldContainIsTruncatedFlag);
    assertEqualsWithExistenceCheck(
        "false",
        () -> response.get("$.data.variables[1].isValueTruncated"),
        shouldContainIsTruncatedFlag);
  }

  @Test
  public void shouldReturnBigVariablesWithPreviewForCompletedTask() throws IOException {
    // having
    final int size =
        tasklistProperties.getImporter().getVariableSizeThreshold()
            - 1; // -1 as we work with string and there is a quote at the beginning

    final String suffix = "999";
    final String varValue1 = createBigVariable(size);
    final String varValue2 = createBigVariable(size);

    final GraphQLResponse response =
        tester
            .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(
                BPMN_PROCESS_ID,
                "{\"var1\": \""
                    + varValue1
                    + suffix
                    + "\", \"var2\": \""
                    + varValue2
                    + suffix
                    + "\"}")
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .claimHumanTask(ELEMENT_ID)
            .and()
            .completeHumanTask(ELEMENT_ID)
            .waitUntil()
            .taskVariableExists("var1")
            .when()
            .getAllTasks(variableFragmentResource);

    // then
    assertTrue(response.isOk());
    assertEquals("1", response.get("$.data.tasks.length()"));

    assertEquals("2", response.get("$.data.tasks[0].variables.length()"));
    assertEquals("var1", response.get("$.data.tasks[0].variables[0].name"));
    assertEqualsWithExistenceCheck(
        "\"" + varValue1 + suffix + "\"",
        () -> response.get("$.data.tasks[0].variables[0].value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + varValue1 + "",
        () -> response.get("$.data.tasks[0].variables[0].previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> response.get("$.data.tasks[0].variables[0].isValueTruncated"),
        shouldContainIsTruncatedFlag);
    final String variableId = response.get("$.data.tasks[0].variables[0].id");
    assertNotNull(variableId);

    assertEquals("var2", response.get("$.data.tasks[0].variables[1].name"));
    assertEqualsWithExistenceCheck(
        "\"" + varValue2 + suffix + "\"",
        () -> response.get("$.data.tasks[0].variables[1].value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + varValue2 + "",
        () -> response.get("$.data.tasks[0].variables[1].previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> response.get("$.data.tasks[0].variables[1].isValueTruncated"),
        shouldContainIsTruncatedFlag);

    // get one variable by id
    // when
    final GraphQLResponse varResponse =
        tester.getVariableById(variableId, variableFragmentResource);

    // then
    assertEquals(variableId, varResponse.get("$.data.variable.id"));
    assertEquals("var1", varResponse.get("$.data.variable.name"));
    assertEqualsWithExistenceCheck(
        "\"" + varValue1 + suffix + "\"",
        () -> varResponse.get("$.data.variable.value"),
        shouldContainFullValue);
    assertEqualsWithExistenceCheck(
        "\"" + varValue1 + "",
        () -> varResponse.get("$.data.variable.previewValue"),
        shouldContainPreview);
    assertEqualsWithExistenceCheck(
        "true",
        () -> varResponse.get("$.data.variable.isValueTruncated"),
        shouldContainIsTruncatedFlag);
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
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals("2", taskResponse.get("$.data.task.variables.length()"));
    assertEquals("processVar", taskResponse.get("$.data.task.variables[0].name"));
    assertEquals("111", taskResponse.get("$.data.task.variables[0].value"));
    assertEquals("subprocessVar", taskResponse.get("$.data.task.variables[1].name"));
    assertEquals("111", taskResponse.get("$.data.task.variables[1].value"));
  }
}
