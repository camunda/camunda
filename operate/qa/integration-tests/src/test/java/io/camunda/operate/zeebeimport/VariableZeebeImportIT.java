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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.qa.util.VariablesUtil.VAR_SUFFIX;
import static io.camunda.operate.qa.util.VariablesUtil.createBigVariable;
import static io.camunda.operate.qa.util.VariablesUtil.createBigVarsWithSuffix;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class VariableZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  @Autowired private UpdateVariableHandler updateVariableHandler;

  @Override
  @Before
  public void before() {
    super.before();
    updateVariableHandler.setZeebeClient(super.getClient());
  }

  protected String getVariablesURL(final Long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/variables", processInstanceKey);
  }

  protected String getVariableURL(final Long processInstanceKey, final String variableId) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/variables/%s", processInstanceKey, variableId);
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // having
    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .subProcess("subProcess")
            .zeebeInput("=var1", "subprocessVarIn")
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task2")
            .zeebeJobType("task2")
            .zeebeInput("=subprocessVarIn", "taskVarIn")
            .zeebeOutput("=taskVarOut", "varOut")
            .endEvent()
            .subProcessDone()
            .serviceTask("task3")
            .zeebeJobType("task3")
            .endEvent()
            .done();
    deployProcess(process, processId + ".bpmn");

    // TC 1 - when process instance is started
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    searchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "otherVar");

    // then
    List<VariableDto> variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(2);
    assertVariable(variables, "var1", "\"initialValue\"");
    assertVariable(variables, "otherVar", "123");
    assertThat(variables).extracting(VariableDto::getIsPreview).containsOnly(false);

    // TC2 - when subprocess and task with input mapping are activated
    completeTask(processInstanceKey, "task1", null);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");
    searchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "taskVarIn");

    // then
    variables = getVariables(processInstanceKey, "subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn", "\"initialValue\"");

    variables = getVariables(processInstanceKey, "task2");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "taskVarIn", "\"initialValue\"");

    // TC3 - when activity with output mapping is completed
    completeTask(
        processInstanceKey, "task2", "{\"taskVarOut\": \"someResult\", \"otherTaskVar\": 456}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task3");
    searchTestRule.processAllRecordsAndWait(
        variableExistsCheck, processInstanceKey, "otherTaskVar");

    // then
    variables = getVariables(processInstanceKey, "task2");
    assertThat(variables).hasSize(3);
    assertVariable(variables, "taskVarIn", "\"initialValue\"");
    assertVariable(variables, "taskVarOut", "\"someResult\"");
    assertVariable(variables, "otherTaskVar", "456");
    variables = getVariables(processInstanceKey, "subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn", "\"initialValue\"");
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, "var1", "\"initialValue\"");
    assertVariable(variables, "varOut", "\"someResult\"");
    assertVariable(variables, "otherVar", "123");

    // TC4 - when variables are updated
    ZeebeTestUtil.updateVariables(
        zeebeClient, processInstanceKey, "{\"var1\": \"updatedValue\" , \"newVar\": 555 }");
    // elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    searchTestRule.processAllRecordsAndWait(
        variableEqualsCheck, processInstanceKey, processInstanceKey, "var1", "\"updatedValue\"");
    searchTestRule.processAllRecordsAndWait(
        variableEqualsCheck, processInstanceKey, processInstanceKey, "newVar", "555");

    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(4);
    assertVariable(variables, "var1", "\"updatedValue\"");
    assertVariable(variables, "otherVar", "123");
    assertVariable(variables, "varOut", "\"someResult\"");
    assertVariable(variables, "newVar", "555");

    // TC5 - when task is completed with new payload and process instance is finished
    completeTask(processInstanceKey, "task3", "{\"task3Completed\": true}");
    searchTestRule.processAllRecordsAndWait(
        variableExistsCheck, processInstanceKey, "task3Completed");

    // then
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(5);
    assertVariable(variables, "var1", "\"updatedValue\"");
    assertVariable(variables, "otherVar", "123");
    assertVariable(variables, "varOut", "\"someResult\"");
    assertVariable(variables, "newVar", "555");
    assertVariable(variables, "task3Completed", "true");
  }

  //  @Test
  //  public void testVariablesPages() throws Exception {
  //    //given
  //    final String bpmnProcessId = "testProcess";
  //    StringBuffer vars = new StringBuffer("{");
  //    for (int i = 0; i < 10; i++) {
  //      if (vars.length() > 1) {
  //        vars.append(",\n");
  //      }
  //      vars.append("\"var").append(i).append("\": \"value_").append(i).append("\"");
  //    }
  //    vars.append("}");
  //    final String flowNodeId = "taskA";
  //    final Long processInstanceKey = tester
  //        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
  //        .startProcessInstance(bpmnProcessId, vars.toString())
  //        .waitUntil()
  //        .flowNodeIsActive(flowNodeId)
  //        .getProcessInstanceKey();
  //
  //    //when requesting page 1
  //    List<VariableDto> variables = getVariables(processInstanceKey, new VariableRequestDto()
  //        .setScopeId(String.valueOf(processInstanceKey))
  //        .setPageSize(3));
  //
  //    //then
  //    assertThat(variables).hasSize(3);
  //    assertThat(variables.get(0).getIsFirst()).isTrue();
  //    for (int i = 0; i < 3; i++) {
  //      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
  //    }
  //    String[] sortValues = variables.get(2).getSortValues();
  //
  //    //when requesting page 2
  //    variables = getVariables(processInstanceKey, new VariableRequestDto()
  //        .setScopeId(String.valueOf(processInstanceKey))
  //        .setSearchAfter(sortValues)
  //        .setPageSize(3));
  //    assertThat(variables).hasSize(3);
  //    assertThat(variables.get(0).getIsFirst()).isFalse();
  //    for (int i = 3; i < 6; i++) {
  //      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
  //    }
  //    sortValues = variables.get(2).getSortValues();
  //
  //    //when requesting page 3
  //    variables = getVariables(processInstanceKey, new VariableRequestDto()
  //        .setScopeId(String.valueOf(processInstanceKey))
  //        .setSearchAfter(sortValues)
  //        .setPageSize(5));
  //    assertThat(variables).hasSize(4);
  //    assertThat(variables.get(0).getIsFirst()).isFalse();
  //    for (int i = 6; i < 10; i++) {
  //      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
  //    }
  //    sortValues = variables.get(3).getSortValues();
  //
  //    //when requesting with searchBefore
  //    variables = getVariables(processInstanceKey, new VariableRequestDto()
  //        .setScopeId(String.valueOf(processInstanceKey))
  //        .setSearchBefore(sortValues)
  //        .setPageSize(5));
  //    assertThat(variables).hasSize(5);
  //    assertThat(variables.get(0).getIsFirst()).isFalse();
  //    for (int i = 4; i <= 8; i++) {
  //      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
  //    }
  //  }

  //  @Test
  //  public void testSecondPageWithActiveOperationOnFirstPage() throws Exception {
  //    //given
  //    final String bpmnProcessId = "testProcess";
  //    StringBuffer vars = new StringBuffer("{");
  //    for (int i = 0; i < 10; i++) {
  //      if (vars.length() > 1) {
  //        vars.append(",\n");
  //      }
  //      vars.append("\"var").append(i).append("\": \"value_").append(i).append("\"");
  //    }
  //    vars.append("}");
  //    final String flowNodeId = "taskA";
  //    final Long processInstanceKey = tester
  //        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
  //        .startProcessInstance(bpmnProcessId, vars.toString())
  //        .waitUntil()
  //        .flowNodeIsActive(flowNodeId)
  //        .getProcessInstanceKey();
  //
  //    //request page 1
  //    List<VariableDto> variables = getVariables(processInstanceKey, new VariableRequestDto()
  //        .setScopeId(String.valueOf(processInstanceKey))
  //        .setPageSize(3));
  //    String[] sortValues = variables.get(2).getSortValues();
  //    //we call UPDATE_VARIABLE operation for the 1st variable (page 1)
  //    final String varName = "var1";
  //    postUpdateVariableOperation(processInstanceKey, varName, "value");
  //    elasticsearchTestRule.refreshOperateESIndices();
  //
  //    //when requesting page 2
  //    variables = getVariables(processInstanceKey, new VariableRequestDto()
  //        .setScopeId(String.valueOf(processInstanceKey))
  //        .setSearchAfter(sortValues)
  //        .setPageSize(3));
  //    assertThat(variables).hasSize(3);
  //    assertThat(variables.get(0).getIsFirst()).isFalse();
  //    for (int i = 3; i < 6; i++) {
  //      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
  //    }
  //  }

  @Test
  public void testVariablesPagesWithAfterBeforeOrEqual() throws Exception {
    // given
    final String bpmnProcessId = "testProcess";
    final StringBuffer vars = new StringBuffer("{");
    for (int i = 0; i < 10; i++) {
      if (vars.length() > 1) {
        vars.append(",\n");
      }
      vars.append("\"var").append(i).append("\": \"value_").append(i).append("\"");
    }
    vars.append("}");
    final String flowNodeId = "taskA";
    final Long processInstanceKey =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
            .startProcessInstance(bpmnProcessId, vars.toString())
            .waitUntil()
            .flowNodeIsActive(flowNodeId)
            .getProcessInstanceKey();

    // when requesting page 1
    List<VariableDto> variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto().setScopeId(String.valueOf(processInstanceKey)).setPageSize(3));

    // then
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isTrue();
    for (int i = 0; i < 3; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    SortValuesWrapper[] sortValues = variables.get(0).getSortValues();

    // when requesting page 1 once again with searchAfterOrEqual
    variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto()
                .setScopeId(String.valueOf(processInstanceKey))
                .setSearchAfterOrEqual(sortValues)
                .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isTrue();
    for (int i = 0; i < 3; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(2).getSortValues();

    // when requesting page 2
    variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto()
                .setScopeId(String.valueOf(processInstanceKey))
                .setSearchAfter(sortValues)
                .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(0).getSortValues();

    // when requesting page 2 once again with searchAfterOrEqual
    variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto()
                .setScopeId(String.valueOf(processInstanceKey))
                .setSearchAfterOrEqual(sortValues)
                .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(2).getSortValues();

    // when requesting page 2 once again with searchBeforeOrEqual
    variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto()
                .setScopeId(String.valueOf(processInstanceKey))
                .setSearchBeforeOrEqual(sortValues)
                .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
  }

  @Test
  public void testBigVariablesWithPreview() throws Exception {
    // given
    final int size = operateProperties.getImporter().getVariableSizeThreshold();
    final String bpmnProcessId = "testProcess";
    final String vars = createBigVarsWithSuffix(size);
    final String flowNodeId = "taskA";
    final Long processInstanceKey =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
            .startProcessInstance(bpmnProcessId, vars)
            .waitUntil()
            .flowNodeIsActive(flowNodeId)
            .getProcessInstanceKey();

    // when requesting list of variables
    final List<VariableDto> variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto()
                .setScopeId(String.valueOf(processInstanceKey))
                .setPageSize(10));

    // then "value" contains truncated value
    final Condition<String> suffix = new Condition<>(s -> s.contains(VAR_SUFFIX), "contains");
    final Condition<String> length = new Condition<>(s -> s.length() == size, "length");
    assertThat(variables).extracting(VariableDto::getValue).areNot(suffix).are(length);
    assertThat(variables).extracting(VariableDto::getIsPreview).containsOnly(true);
    final String variableId = variables.get(0).getId();

    // when requesting one variable
    final VariableDto variable = getOneVariable(processInstanceKey, variableId);

    // then
    assertThat(variable.getValue()).contains(VAR_SUFFIX);
    assertThat(variable.getIsPreview()).isFalse();
  }

  @Test
  public void testUpdateBigVariableWithSmallValue() throws Exception {
    // given
    final int size = operateProperties.getImporter().getVariableSizeThreshold();
    final String bpmnProcessId = "testProcess";
    final String vars = createBigVarsWithSuffix(size);
    final String flowNodeId = "taskA";
    final Long processInstanceKey =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
            .startProcessInstance(bpmnProcessId, vars)
            .waitUntil()
            .flowNodeIsActive(flowNodeId)
            .getProcessInstanceKey();

    // we call UPDATE_VARIABLE operation on instance
    final String varName = "var1";
    final String newVarValue = "\"aaa\"";
    postUpdateVariableOperation(processInstanceKey, varName, newVarValue);
    searchTestRule.refreshOperateSearchIndices();
    // execute the operation
    executeOneBatch();
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreCompleted, processInstanceKey);

    // when one variable is requested
    final VariableDto variable =
        getOneVariable(processInstanceKey, String.format("%d-%s", processInstanceKey, varName));

    // then variable with new small value is returned
    assertThat(variable.getValue()).isEqualTo(newVarValue);
    assertThat(variable.getIsPreview()).isFalse();

    // when get list of variables
    final List<VariableDto> variables = getVariables(processInstanceKey);

    // then new value is returned
    final Optional<VariableDto> var =
        variables.stream().filter(v -> v.getName().equals(varName)).findFirst();
    assertThat(var).isNotEmpty();
    assertThat(var.get().getValue()).isEqualTo(newVarValue);
    assertThat(var.get().getIsPreview()).isEqualTo(false);
  }

  @Test
  public void testBigVariablesWithActiveOperations() throws Exception {
    // given
    final int size = operateProperties.getImporter().getVariableSizeThreshold();
    final String bpmnProcessId = "testProcess";
    final String vars = createBigVarsWithSuffix(size);
    final String flowNodeId = "taskA";
    final Long processInstanceKey =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
            .startProcessInstance(bpmnProcessId, vars)
            .waitUntil()
            .flowNodeIsActive(flowNodeId)
            .getProcessInstanceKey();

    // when
    // we call UPDATE_VARIABLE operation on instance
    final String varName = "var1";
    final String newVarValue = createBigVariable(size);
    final String newVarName = "newVar";
    postUpdateVariableOperation(processInstanceKey, varName, newVarValue + VAR_SUFFIX);
    postAddVariableOperation(processInstanceKey, newVarName, newVarValue + VAR_SUFFIX);
    searchTestRule.refreshOperateSearchIndices();

    // then variable with new value is returned
    final List<VariableDto> variables = getVariables(processInstanceKey);

    // then "value" contains truncated value
    assertThat(variables)
        .filteredOn(v -> v.getName().equals(varName))
        .extracting(VariableDto::getValue)
        .containsOnly(newVarValue);
    // new variable should not be returned in this endpoint
    assertThat(variables).filteredOn(v -> v.getName().equals(newVarName)).isEmpty();
  }

  @Test
  public void testVariablesRequestFailOnEmptyScopeId() throws Exception {
    final MvcResult mvcResult =
        mockMvc
            .perform(
                post(getVariablesURL(0L))
                    .content(mockMvcTestRule.json(new VariableRequestDto()))
                    .contentType(mockMvcTestRule.getContentType()))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage())
        .isEqualTo("ScopeId must be specifies in the request.");
  }

  private void assertVariable(
      final List<VariableDto> variables, final String name, final String value) {
    assertThat(variables)
        .filteredOn(v -> v.getName().equals(name))
        .hasSize(1)
        .allMatch(v -> v.getValue().equals(value));
  }

  protected List<VariableDto> getVariables(final Long processInstanceKey) throws Exception {
    final MvcResult mvcResult =
        mockMvc
            .perform(
                post(getVariablesURL(processInstanceKey))
                    .content(
                        mockMvcTestRule.json(
                            new VariableRequestDto()
                                .setScopeId(String.valueOf(processInstanceKey))))
                    .contentType(mockMvcTestRule.getContentType()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected List<VariableDto> getVariables(final Long processInstanceKey, final String activityId)
      throws Exception {
    final List<FlowNodeInstanceEntity> allActivityInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    final Long task1Id = findActivityInstanceId(allActivityInstances, activityId);
    final MvcResult mvcResult =
        mockMvc
            .perform(
                post(getVariablesURL(processInstanceKey))
                    .content(
                        mockMvcTestRule.json(
                            new VariableRequestDto().setScopeId(String.valueOf(task1Id))))
                    .contentType(mockMvcTestRule.getContentType()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected List<VariableDto> getVariables(
      final Long processInstanceKey, final VariableRequestDto request) throws Exception {
    final MvcResult mvcResult =
        mockMvc
            .perform(
                post(getVariablesURL(processInstanceKey))
                    .content(mockMvcTestRule.json(request))
                    .contentType(mockMvcTestRule.getContentType()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected VariableDto getOneVariable(final Long processInstanceKey, final String variableId)
      throws Exception {
    final MvcResult mvcResult =
        mockMvc
            .perform(get(getVariableURL(processInstanceKey, variableId)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();
    return mockMvcTestRule.fromResponse(mvcResult, VariableDto.class);
  }

  protected Long findActivityInstanceId(
      final List<FlowNodeInstanceEntity> allActivityInstances, final String activityId) {
    assertThat(allActivityInstances)
        .filteredOn(ai -> ai.getFlowNodeId().equals(activityId))
        .hasSize(1);
    return Long.valueOf(
        allActivityInstances.stream()
            .filter(ai -> ai.getFlowNodeId().equals(activityId))
            .findFirst()
            .get()
            .getId());
  }
}
