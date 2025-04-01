/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class ProcessZeebeImportIT extends OperateZeebeAbstractIT {

  private static final String QUERY_PROCESSES_GROUPED_URL = "/api/processes/grouped";
  private static final String QUERY_PROCESS_XML_URL = "/api/processes/%s/xml";

  @Autowired private ProcessReader processReader;

  @MockBean private PermissionsService permissionsService;

  @Override
  @Before
  public void before() {
    super.before();
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(any(), any())).thenReturn(true);
  }

  @Test
  public void testProcessCreated() {
    // when
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    // then
    final ProcessEntity processEntity = processReader.getProcess(processDefinitionKey);
    assertThat(processEntity.getKey()).isEqualTo(processDefinitionKey);
    assertThat(processEntity.getBpmnProcessId()).isEqualTo("demoProcess");
    assertThat(processEntity.getVersion()).isEqualTo(1);
    assertThat(processEntity.getBpmnXml()).isNotEmpty();
    assertThat(processEntity.getName()).isEqualTo("Demo process");
    assertThat(processEntity.getVersionTag()).isEqualTo("demo-tag_v1");
  }

  @Test
  public void testProcessGetDiagram() throws Exception {
    // given
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    final MockHttpServletRequestBuilder request =
        get(String.format(QUERY_PROCESS_XML_URL, processDefinitionKey));

    final MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    final String xml = mvcResult.getResponse().getContentAsString();

    // then
    assertThat(xml).isNotEmpty();
    assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
  }

  @Test
  public void testProcessesGrouped() throws Exception {
    // given
    final String demoProcessId = "demoProcess";
    final String demoProcessName = "Demo process new name";
    final String orderProcessId = "orderProcess";
    final String orderProcessName = "Order process";
    final String loanProcessId = "loanProcess";
    final Long demoProcessV1Id =
        createAndDeployProcess(super.getClient(), demoProcessId, "Demo process");
    final Long demoProcessV2Id =
        createAndDeployProcess(super.getClient(), demoProcessId, demoProcessName);
    final Long orderProcessV1Id =
        createAndDeployProcess(super.getClient(), orderProcessId, orderProcessName);
    final Long orderProcessV2Id =
        createAndDeployProcess(super.getClient(), orderProcessId, orderProcessName);
    final Long orderProcessV3Id =
        createAndDeployProcess(super.getClient(), orderProcessId, orderProcessName);
    final Long loanProcessV1Id = createAndDeployProcess(super.getClient(), loanProcessId, null);

    // when
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, loanProcessV1Id);
    // elasticsearchTestRule.refreshIndexesInElasticsearch();

    // then
    final MockHttpServletRequestBuilder request = get(QUERY_PROCESSES_GROUPED_URL);

    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();

    final List<ProcessGroupDto> processGroupDtos =
        mockMvcTestRule.listFromResponse(mvcResult, ProcessGroupDto.class);
    assertThat(processGroupDtos).hasSize(3);
    assertThat(processGroupDtos).isSortedAccordingTo(new ProcessGroupDto.ProcessGroupComparator());

    assertThat(processGroupDtos)
        .filteredOn(wg -> wg.getBpmnProcessId().equals(demoProcessId))
        .hasSize(1);
    final ProcessGroupDto demoProcessProcessGroup =
        processGroupDtos.stream()
            .filter(wg -> wg.getBpmnProcessId().equals(demoProcessId))
            .findFirst()
            .get();
    assertThat(demoProcessProcessGroup.getProcesses()).hasSize(2);
    assertThat(demoProcessProcessGroup.getName()).isEqualTo(demoProcessName);
    assertThat(demoProcessProcessGroup.getProcesses())
        .isSortedAccordingTo(
            (w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoProcessProcessGroup.getProcesses())
        .extracting(ProcessIndex.ID)
        .containsExactlyInAnyOrder(demoProcessV1Id.toString(), demoProcessV2Id.toString());

    assertThat(processGroupDtos)
        .filteredOn(wg -> wg.getBpmnProcessId().equals(orderProcessId))
        .hasSize(1);
    final ProcessGroupDto orderProcessProcessGroup =
        processGroupDtos.stream()
            .filter(wg -> wg.getBpmnProcessId().equals(orderProcessId))
            .findFirst()
            .get();
    assertThat(orderProcessProcessGroup.getProcesses()).hasSize(3);
    assertThat(orderProcessProcessGroup.getName()).isEqualTo(orderProcessName);
    assertThat(orderProcessProcessGroup.getProcesses())
        .isSortedAccordingTo(
            (w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(orderProcessProcessGroup.getProcesses())
        .extracting(ProcessIndex.ID)
        .containsExactlyInAnyOrder(
            orderProcessV1Id.toString(), orderProcessV2Id.toString(), orderProcessV3Id.toString());

    assertThat(processGroupDtos)
        .filteredOn(wg -> wg.getBpmnProcessId().equals(loanProcessId))
        .hasSize(1);
    final ProcessGroupDto loanProcessProcessGroup =
        processGroupDtos.stream()
            .filter(wg -> wg.getBpmnProcessId().equals(loanProcessId))
            .findFirst()
            .get();
    assertThat(loanProcessProcessGroup.getName()).isNull();
    assertThat(loanProcessProcessGroup.getProcesses()).hasSize(1);
    assertThat(loanProcessProcessGroup.getProcesses().get(0).getId())
        .isEqualTo(loanProcessV1Id.toString());

    verify(permissionsService, times(3)).getProcessDefinitionPermissions(anyString());
  }

  private Long createAndDeployProcess(
      final CamundaClient camundaClient, final String bpmnProcessId, final String name) {
    ProcessBuilder executableProcess = Bpmn.createExecutableProcess(bpmnProcessId);
    if (name != null) {
      executableProcess = executableProcess.name(name);
    }
    final BpmnModelInstance demoProcess = executableProcess.startEvent().endEvent().done();
    return ZeebeTestUtil.deployProcess(camundaClient, null, demoProcess, "resource.bpmn");
  }
}
