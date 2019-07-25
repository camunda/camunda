/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.rest.dto.WorkflowGroupDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;

public class WorkflowIT extends OperateZeebeIntegrationTest {

  private static final String QUERY_WORKFLOWS_GROUPED_URL = "/api/workflows/grouped";
  private static final String QUERY_WORKFLOW_XML_URL = "/api/workflows/%s/xml";

  @Autowired
  private WorkflowReader workflowReader;

  @Test
  public void testWorkflowCreated() {
    //when
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");

    //then
    final WorkflowEntity workflowEntity = workflowReader.getWorkflow(workflowKey);
    assertThat(workflowEntity.getKey()).isEqualTo(workflowKey);
    assertThat(workflowEntity.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(workflowEntity.getVersion()).isEqualTo(1);
    assertThat(workflowEntity.getBpmnXml()).isNotEmpty();
    assertThat(workflowEntity.getName()).isEqualTo("Demo process");
  }

  @Test
  public void testTwoWorkflowsFromOneDeploymentCreated() {
    //when
    String bpmnProcessId1 = "demoProcess";
    String bpmnProcessId2 = "processWithGateway";
    deployWorkflow("demoProcess_v_1.bpmn", "processWithGateway.bpmn");

    //then
    final Map<Long, WorkflowEntity> workflows = workflowReader.getWorkflows();
    assertThat(workflows).hasSize(2);

    assertThat(workflows.values()).extracting("bpmnProcessId").containsExactlyInAnyOrder(bpmnProcessId1, bpmnProcessId2);
  }

  @Test
  public void testWorkflowGetDiagram() throws Exception {
    //given
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");

    MockHttpServletRequestBuilder request = get(String.format(QUERY_WORKFLOW_XML_URL, workflowKey));

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andReturn();

    final String xml = mvcResult.getResponse().getContentAsString();

    //then
    assertThat(xml).isNotEmpty();
    assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
  }

  @Test
  public void testWorkflowsGrouped() throws Exception {
    //given
    final String demoProcessId = "demoProcess";
    final String demoProcessName = "Demo process new name";
    final String orderProcessId = "orderProcess";
    final String orderProcessName = "Order process";
    final String loanProcessId = "loanProcess";
    final Long demoProcessV1Id = createAndDeployProcess(super.getClient(), demoProcessId, "Demo process");
    final Long demoProcessV2Id = createAndDeployProcess(super.getClient(), demoProcessId, demoProcessName);
    final Long orderProcessV1Id = createAndDeployProcess(super.getClient(), orderProcessId, orderProcessName);
    final Long orderProcessV2Id = createAndDeployProcess(super.getClient(), orderProcessId, orderProcessName);
    final Long orderProcessV3Id = createAndDeployProcess(super.getClient(), orderProcessId, orderProcessName);
    final Long loanProcessV1Id = createAndDeployProcess(super.getClient(), loanProcessId, null);

    //when
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, loanProcessV1Id);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    MockHttpServletRequestBuilder request = get(QUERY_WORKFLOWS_GROUPED_URL);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowGroupDto> workflowGroupDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowGroupDto.class);
    assertThat(workflowGroupDtos).hasSize(3);
    assertThat(workflowGroupDtos).isSortedAccordingTo(new WorkflowGroupDto.WorkflowGroupComparator());

    assertThat(workflowGroupDtos).filteredOn(wg -> wg.getBpmnProcessId().equals(demoProcessId)).hasSize(1);
    final WorkflowGroupDto demoProcessWorkflowGroup =
      workflowGroupDtos.stream().filter(wg -> wg.getBpmnProcessId().equals(demoProcessId)).findFirst().get();
    assertThat(demoProcessWorkflowGroup.getWorkflows()).hasSize(2);
    assertThat(demoProcessWorkflowGroup.getName()).isEqualTo(demoProcessName);
    assertThat(demoProcessWorkflowGroup.getWorkflows()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoProcessWorkflowGroup.getWorkflows()).extracting(WorkflowIndex.ID).containsExactlyInAnyOrder(demoProcessV1Id.toString(), demoProcessV2Id.toString());

    assertThat(workflowGroupDtos).filteredOn(wg -> wg.getBpmnProcessId().equals(orderProcessId)).hasSize(1);
    final WorkflowGroupDto orderProcessWorkflowGroup =
      workflowGroupDtos.stream().filter(wg -> wg.getBpmnProcessId().equals(orderProcessId)).findFirst().get();
    assertThat(orderProcessWorkflowGroup.getWorkflows()).hasSize(3);
    assertThat(orderProcessWorkflowGroup.getName()).isEqualTo(orderProcessName);
    assertThat(orderProcessWorkflowGroup.getWorkflows()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(orderProcessWorkflowGroup.getWorkflows()).extracting(WorkflowIndex.ID).containsExactlyInAnyOrder(orderProcessV1Id.toString(), orderProcessV2Id.toString(), orderProcessV3Id.toString());


    assertThat(workflowGroupDtos).filteredOn(wg -> wg.getBpmnProcessId().equals(loanProcessId)).hasSize(1);
    final WorkflowGroupDto loanProcessWorkflowGroup =
      workflowGroupDtos.stream().filter(wg -> wg.getBpmnProcessId().equals(loanProcessId)).findFirst().get();
    assertThat(loanProcessWorkflowGroup.getName()).isNull();
    assertThat(loanProcessWorkflowGroup.getWorkflows()).hasSize(1);
    assertThat(loanProcessWorkflowGroup.getWorkflows().get(0).getId()).isEqualTo(loanProcessV1Id.toString());
  }

  private Long createAndDeployProcess(ZeebeClient zeebeClient, String bpmnProcessId, String name) {
    ProcessBuilder executableProcess = Bpmn.createExecutableProcess(bpmnProcessId);
    if (name != null) {
      executableProcess = executableProcess.name(name);
    }
    final BpmnModelInstance demoProcess =
      executableProcess.startEvent().endEvent().done();
    return ZeebeTestUtil.deployWorkflow(zeebeClient, demoProcess, "resource.bpmn");
  }

}
