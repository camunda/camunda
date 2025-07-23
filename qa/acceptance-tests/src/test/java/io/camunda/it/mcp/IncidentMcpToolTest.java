/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.response.Incident;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchResponse;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.JsonUtil;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class IncidentMcpToolTest {

  @Authenticated
  private static McpSyncClient mcpClient;

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withProperty("camunda.gateway.mcp.enabled", true);

  private static final List<Process> deployedProcesses = new ArrayList<>();
  private static final int AMOUNT_OF_INCIDENTS = 3;
  private static Incident incident;

  @BeforeAll
  public static void setup(@Authenticated CamundaClient camundaClient) {
    final var processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process ->
            deployedProcesses.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, 3);

    startProcessInstance(camundaClient, "service_tasks_v1");
    startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");

    waitForProcessInstancesToStart(camundaClient, 5);
    waitUntilProcessInstanceHasIncidents(camundaClient, AMOUNT_OF_INCIDENTS);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    incident = camundaClient.newIncidentSearchRequest().send().join().items().getFirst();

    mcpClient.initialize();
  }

  @Test
  void shouldSuccessfullyRetrieveIncidentsByProcessDefinitionKeys() {
    // when
    var callResult = mcpClient.callTool(new CallToolRequest("searchIncidents", """
        {
           "filter": {
              "processDefinitionIds": ["%s"]
           }
        }
        """.formatted("incident_process_v1")));

    // then
    assertThat(callResult.isError()).isFalse();

    // Parse the result content to IncidentSearchResponse using JsonUtil
    var responseContent = (TextContent) callResult.content().getFirst();
    var incidentSearchResponse = JsonUtil.fromJson(responseContent.text(), IncidentSearchResponse.class);

    // Assert that the number of incidents equals 3
    assertThat(incidentSearchResponse.incidents()).hasSize(AMOUNT_OF_INCIDENTS);
  }
}
