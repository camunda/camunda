/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class SequenceFlowZeebeImportIT extends OperateZeebeAbstractIT {

  protected String getSequenceFlowURL(final Long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/sequence-flows", processInstanceKey);
  }

  @Test
  public void testSequenceFlowsAreLoaded() throws Exception {
    // having
    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .sequenceFlowId("sf1")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .sequenceFlowId("sf2")
            .serviceTask("task2")
            .zeebeJobType("task2")
            .endEvent()
            .done();
    deployProcess(process, processId + ".bpmn");

    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            camundaClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    ZeebeTestUtil.completeTask(camundaClient, "task1", getWorkerName(), null);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");

    // when
    final List<SequenceFlowDto> sequenceFlows = getSequenceFlows(processInstanceKey);

    assertThat(sequenceFlows)
        .extracting(SequenceFlowTemplate.ACTIVITY_ID)
        .containsExactlyInAnyOrder("sf1", "sf2");
  }

  private List<SequenceFlowDto> getSequenceFlows(final Long processInstanceKey) throws Exception {
    final MvcResult mvcResult =
        mockMvc
            .perform(get(getSequenceFlowURL(processInstanceKey)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, SequenceFlowDto.class);
  }
}
