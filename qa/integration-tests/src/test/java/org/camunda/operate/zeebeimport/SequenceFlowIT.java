/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import org.camunda.operate.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class SequenceFlowIT extends OperateZeebeIntegrationTest {

  protected String getSequenceFlowURL(Long workflowInstanceKey) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/sequence-flows", workflowInstanceKey);
  }

  @Test
  public void testSequenceFlowsAreLoaded() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .sequenceFlowId("sf1")
        .serviceTask("task1").zeebeJobType("task1")
        .sequenceFlowId("sf2")
        .serviceTask("task2").zeebeJobType("task2")
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");

    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "task1");
    ZeebeTestUtil.completeTask(zeebeClient, "task1", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "task2");


    //when
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
