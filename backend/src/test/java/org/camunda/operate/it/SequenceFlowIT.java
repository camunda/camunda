/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.rest.dto.SequenceFlowDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SequenceFlowIT extends OperateZeebeIntegrationTest {

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private ZeebeClient zeebeClient;

  private MockMvc mockMvc;

  @Before
  public void init() {
    super.before();
    zeebeClient = super.getClient();
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  protected String getSequenceFlowURL(String workflowInstanceId) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/sequence-flows", workflowInstanceId);
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .sequenceFlowId("sf1")
        .serviceTask("task1").zeebeTaskType("task1")
        .sequenceFlowId("sf2")
        .serviceTask("task2").zeebeTaskType("task2")
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");

    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");
    ZeebeTestUtil.completeTask(zeebeClient, "task1", getWorkerName(), null);
    final String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task2");


    //when
    List<SequenceFlowDto> sequenceFlows = getSequenceFlows(workflowInstanceId);

    assertThat(sequenceFlows).extracting(SequenceFlowTemplate.ACTIVITY_ID).containsExactlyInAnyOrder("sf1", "sf2");
  }

  private List<SequenceFlowDto> getSequenceFlows(String workflowInstanceId) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(get(getSequenceFlowURL(workflowInstanceId)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, SequenceFlowDto.class);
  }

}
