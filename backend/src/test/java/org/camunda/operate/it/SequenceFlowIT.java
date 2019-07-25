/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.rest.dto.SequenceFlowDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class SequenceFlowIT extends OperateZeebeIntegrationTest {

  @Autowired
  OperateTester tester;
  
  @Before
  public void before() {
    super.before();
    tester.setZeebeClient(getClient());
  }

  protected String getSequenceFlowURL(Long workflowInstanceKey) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/sequence-flows", workflowInstanceKey);
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // given
    String processId = "demoProcess";
    
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .sequenceFlowId("sf1")
        .serviceTask("task1").zeebeTaskType("task1")
        .sequenceFlowId("sf2")
        .serviceTask("task2").zeebeTaskType("task2")
      .endEvent()
      .done();
    
    Long workflowInstanceKey = tester
      .deployWorkflow(workflow,processId +".bpmn")
      .startWorkflowInstance(processId,"{\"var1\": \"initialValue\", \"otherVar\": 123}")
      .waitUntil().activityIsActive("task1")
      .completeTask("task1")
      .waitUntil().activityIsActive("task2")
      .and()
      .getWorkflowInstanceKey();
    
    // when
    List<SequenceFlowDto> sequenceFlows = getSequenceFlows(workflowInstanceKey);

    assertThat(sequenceFlows).extracting(SequenceFlowTemplate.ACTIVITY_ID).containsExactlyInAnyOrder("sf1", "sf2");
  }

  private List<SequenceFlowDto> getSequenceFlows(Long workflowInstanceKey) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(get(getSequenceFlowURL(workflowInstanceKey)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, SequenceFlowDto.class);
  }

}
