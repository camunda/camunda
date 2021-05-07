/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

@Deprecated
public class OldVariableIT extends OperateZeebeIntegrationTest {

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  protected String getVariablesURL(Long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/variables", processInstanceKey);
  }

  protected String getVariablesURL(Long processInstanceKey, Long scopeKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/variables?scopeId=%s", processInstanceKey, scopeKey);
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask("task1").zeebeJobType("task1")
        .subProcess("subProcess")
        .zeebeInput("=var1", "subprocessVarIn")
        .embeddedSubProcess()
        .startEvent()
        .serviceTask("task2").zeebeJobType("task2")
        .zeebeInput("=subprocessVarIn", "taskVarIn")
        .zeebeOutput("=taskVarOut", "varOut")
        .endEvent()
        .subProcessDone()
        .serviceTask("task3").zeebeJobType("task3")
        .endEvent()
        .done();
    deployProcess(process, processId + ".bpmn");

    //TC 1 - when process instance is started
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "otherVar");

    //then
    List<VariableDto> variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(2);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "otherVar","123");

    //TC2 - when subprocess and task with input mapping are activated
    completeTask(processInstanceKey, "task1", null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "taskVarIn");


    //then
    variables = getVariables(processInstanceKey,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");

    variables = getVariables(processInstanceKey,"task2");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "taskVarIn","\"initialValue\"");

    //TC3 - when activity with output mapping is completed
    completeTask(processInstanceKey, "task2", "{\"taskVarOut\": \"someResult\", \"otherTaskVar\": 456}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task3");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "otherTaskVar");

    //then
    variables = getVariables(processInstanceKey,"task2");
    assertThat(variables).hasSize(3);
    assertVariable(variables, "taskVarIn","\"initialValue\"");
    assertVariable(variables, "taskVarOut","\"someResult\"");
    assertVariable(variables, "otherTaskVar","456");
    variables = getVariables(processInstanceKey,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "otherVar","123");

    //TC4 - when variables are updated
    ZeebeTestUtil.updateVariables(zeebeClient, processInstanceKey, "{\"var1\": \"updatedValue\" , \"newVar\": 555 }");
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, processInstanceKey,processInstanceKey,"var1","\"updatedValue\"");
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, processInstanceKey,processInstanceKey,"newVar","555");

    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(4);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");

    //TC5 - when task is completed with new payload and process instance is finished
    completeTask(processInstanceKey, "task3", "{\"task3Completed\": true}");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "task3Completed");

    //then
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(5);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");
    assertVariable(variables, "task3Completed","true");

  }

  @Test
  public void testVariablesRequestFailOnEmptyScopeId() throws Exception {
    MvcResult mvcResult = mockMvc.perform(get(getVariablesURL(0L)))
        .andExpect(status().isBadRequest())
        .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Required request parameter 'scopeId' for method parameter type String is not present");
  }

  private void assertVariable(List<VariableDto> variables, String name, String value) {
    assertThat(variables).filteredOn(v -> v.getName().equals(name)).hasSize(1)
        .allMatch(v -> v.getValue().equals(value));
  }

  protected List<VariableDto> getVariables(Long processInstanceKey) throws Exception {
    MvcResult mvcResult = mockMvc
        .perform(get(getVariablesURL(processInstanceKey, processInstanceKey)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected List<VariableDto> getVariables(Long processInstanceKey, String activityId) throws Exception {
    final List<FlowNodeInstanceEntity> allActivityInstances = tester.getAllFlowNodeInstances(processInstanceKey);
    final Long task1Id = findActivityInstanceId(allActivityInstances, activityId);
    MvcResult mvcResult = mockMvc
        .perform(get(getVariablesURL(processInstanceKey, task1Id)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected Long findActivityInstanceId(List<FlowNodeInstanceEntity> allActivityInstances, String activityId) {
    assertThat(allActivityInstances).filteredOn(ai -> ai.getFlowNodeId().equals(activityId)).hasSize(1);
    return Long.valueOf(allActivityInstances.stream().filter(ai -> ai.getFlowNodeId().equals(activityId)).findFirst().get().getId());
  }

}
