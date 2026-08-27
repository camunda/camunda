/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForAgentDefinitionsToBeIndexed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class AgentDefinitionFetchIT {

  private static final String SUB_PROCESS_ELEMENT_ID = "explicitNameAgent";
  private static final String TASK_ELEMENT_ID = "fallbackNameAgent";
  private static final String EXPLICIT_NAME = "Explicit Agent Name";

  private static CamundaClient camundaClient;

  private static long processDefinitionKey;
  private static String bpmnProcessId;

  // agentDefinition1: AI_AGENT_SUB_PROCESS with an explicit BPMN name
  private static long agentDefinitionKey1;

  // agentDefinition2: AI_AGENT_TASK with no explicit name (falls back to elementId)
  private static long agentDefinitionKey2;

  @BeforeAll
  static void setup() {
    final var processModel =
        Bpmn.createExecutableProcess("AgentDefinitionFetchProcess")
            .startEvent()
            .adHocSubProcess(
                SUB_PROCESS_ELEMENT_ID,
                ahsp ->
                    ahsp.name(EXPLICIT_NAME)
                        .zeebeJobType("agent-fetch-sub-process-job")
                        .zeebeAiAgentSubProcessDefinition()
                        .task("inner"))
            .serviceTask(
                TASK_ELEMENT_ID,
                t -> t.zeebeJobType("agent-fetch-task-job").zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    final var process =
        deployProcessAndWaitForIt(camundaClient, processModel, "agent-definition-fetch.bpmn");
    processDefinitionKey = process.getProcessDefinitionKey();
    bpmnProcessId = process.getBpmnProcessId();

    waitForAgentDefinitionsToBeIndexed(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 2);

    agentDefinitionKey1 = fetchAgentDefinitionKey(SUB_PROCESS_ELEMENT_ID);
    agentDefinitionKey2 = fetchAgentDefinitionKey(TASK_ELEMENT_ID);
  }

  private static long fetchAgentDefinitionKey(final String elementId) {
    return camundaClient
        .newAgentDefinitionSearchRequest()
        .filter(f -> f.processDefinitionKey(processDefinitionKey).elementId(elementId))
        .execute()
        .items()
        .getFirst()
        .getAgentDefinitionKey();
  }

  @Test
  void shouldGetAgentDefinitionWithExplicitName() {
    // when
    final var response = camundaClient.newAgentDefinitionGetRequest(agentDefinitionKey1).execute();

    // then
    assertSoftly(
        softly -> {
          softly
              .assertThat(response.getAgentDefinitionKey())
              .as("agentDefinitionKey")
              .isEqualTo(agentDefinitionKey1);
          softly
              .assertThat(response.getAgentType())
              .as("agentType")
              .isEqualTo(AgentDefinitionType.AI_AGENT_SUB_PROCESS);
          softly.assertThat(response.getName()).as("name").isEqualTo(EXPLICIT_NAME);
          softly
              .assertThat(response.getElementId())
              .as("elementId")
              .isEqualTo(SUB_PROCESS_ELEMENT_ID);
          softly
              .assertThat(response.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo(bpmnProcessId);
          softly
              .assertThat(response.getProcessDefinitionKey())
              .as("processDefinitionKey")
              .isEqualTo(processDefinitionKey);
          softly
              .assertThat(response.getProcessDefinitionVersion())
              .as("processDefinitionVersion")
              .isGreaterThan(0);
          softly
              .assertThat(response.getProcessDefinitionVersionTag())
              .as("processDefinitionVersionTag")
              .isNull();
          softly.assertThat(response.getTenantId()).as("tenantId").isNotNull();
        });
  }

  @Test
  void shouldGetAgentDefinitionWithNameFallenBackToElementId() {
    // when
    final var response = camundaClient.newAgentDefinitionGetRequest(agentDefinitionKey2).execute();

    // then
    assertSoftly(
        softly -> {
          softly
              .assertThat(response.getAgentDefinitionKey())
              .as("agentDefinitionKey")
              .isEqualTo(agentDefinitionKey2);
          softly
              .assertThat(response.getAgentType())
              .as("agentType")
              .isEqualTo(AgentDefinitionType.AI_AGENT_TASK);
          softly.assertThat(response.getName()).as("name").isEqualTo(TASK_ELEMENT_ID);
          softly.assertThat(response.getElementId()).as("elementId").isEqualTo(TASK_ELEMENT_ID);
        });
  }

  @Test
  void shouldReturnNotFoundForUnknownKey() {
    // when
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newAgentDefinitionGetRequest(Long.MAX_VALUE).execute())
            .actual();

    // then
    assertThat(problemException.code()).as("HTTP status code").isEqualTo(404);
    final ProblemDetail details = problemException.details();
    assertThat(details.getDetail())
        .as("problem detail should name the missing agent definition key")
        .contains("Agent Definition with key '%d' not found".formatted(Long.MAX_VALUE));
  }
}
