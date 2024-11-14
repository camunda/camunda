/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.v86.entities.ProcessEntity;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeImportMultipleProcessesIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessStore processStore;
  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldImportBpmnWithMultipleProcesses() {
    final String bpmnProcessId1 = "Process_0diikxu";
    final String bpmnProcessId2 = "Process_18z2cdf";

    tester.deployProcess("two_processes.bpmn").waitUntil().processIsDeployed();

    assertThat(mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1)))
        .hasOkHttpStatus()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .hasSize(2)
        .extracting("bpmnProcessId", "name")
        .containsExactlyInAnyOrder(
            Tuple.tuple(bpmnProcessId1, "Business Operation A"),
            Tuple.tuple(bpmnProcessId2, "Business Operation B"));

    final ProcessEntity processEntity1 = processStore.getProcessByBpmnProcessId(bpmnProcessId1);
    assertEquals(1, processEntity1.getFlowNodes().size());
    assertEquals("Do task A", processEntity1.getFlowNodes().get(0).getName());

    final ProcessEntity processEntity2 = processStore.getProcessByBpmnProcessId(bpmnProcessId2);
    assertEquals(1, processEntity2.getFlowNodes().size());
    assertEquals("Do task B", processEntity2.getFlowNodes().get(0).getName());
  }
}
