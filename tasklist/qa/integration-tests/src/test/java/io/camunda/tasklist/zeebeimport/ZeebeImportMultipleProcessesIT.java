/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.time.Duration;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
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

    tester.deployProcess("two_processes.bpmn");

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                processStore
                        .getProcesses(List.of(bpmnProcessId1, bpmnProcessId2), "<default>", null)
                        .size()
                    == 2);

    assertThat(mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1)))
        .hasOkHttpStatus()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .hasSize(2)
        .extracting("bpmnProcessId", "name")
        .containsExactlyInAnyOrder(
            Tuple.tuple(bpmnProcessId1, "Business Operation A"),
            Tuple.tuple(bpmnProcessId2, "Business Operation B"));

    final ProcessEntity processEntity1 = processStore.getProcessByBpmnProcessId(bpmnProcessId1);
    Assertions.assertThat(processEntity1.getFlowNodes())
        .filteredOn(flowNode -> flowNode.getName().equals("Do task A"))
        .isNotEmpty();

    Assertions.assertThat(processEntity1.getFlowNodes())
        .filteredOn(flowNode -> flowNode.getName().equals("Do task B"))
        .isEmpty();

    final ProcessEntity processEntity2 = processStore.getProcessByBpmnProcessId(bpmnProcessId2);
    Assertions.assertThat(processEntity2.getFlowNodes())
        .filteredOn(flowNode -> flowNode.getName().equals("Do task B"))
        .isNotEmpty();

    Assertions.assertThat(processEntity2.getFlowNodes())
        .filteredOn(flowNode -> flowNode.getName().equals("Do task A"))
        .isEmpty();
  }
}
