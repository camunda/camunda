/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForAgentInstanceToBeIndexed;
import static io.camunda.it.util.TestHelper.waitForAgentInstanceWithStatusToBeIndexed;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.AgentTool;
import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.response.AgentInstance.Tool;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class AgentInstanceFetchIT {

  private static final String AGENT_ELEMENT_ID = "agentAhsp";

  private static CamundaClient camundaClient;

  // agentInstance1: created with required fields only (no limits, no tools)
  private static long agentInstanceKey1;
  private static long processInstanceKey1;

  // agentInstance2: created with limits and updated with tools
  private static long agentInstanceKey2;
  private static long processInstanceKey2;

  private static String bpmnProcessId;

  @BeforeAll
  static void setup() {
    final var processModel =
        Bpmn.createExecutableProcess("AgentInstanceFetchProcess")
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .moveToActivity(AGENT_ELEMENT_ID)
            .endEvent("end")
            .done();

    final var process = deployProcessAndWaitForIt(camundaClient, processModel, "agent-fetch.bpmn");
    bpmnProcessId = process.getBpmnProcessId();

    // — agentInstance1: minimal CREATE (no limits, no tools) —
    final var pi1 = startProcessInstance(camundaClient, bpmnProcessId);
    processInstanceKey1 = pi1.getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey1),
        1);
    final var ei1 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey1))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    agentInstanceKey1 =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ei1)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .send()
            .join()
            .getAgentInstanceKey();

    // — agentInstance2: CREATE with limits + UPDATE with tools —
    final var pi2 = startProcessInstance(camundaClient, bpmnProcessId);
    processInstanceKey2 = pi2.getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey2),
        1);
    final var ei2 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey2))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    agentInstanceKey2 =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ei2)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .maxTokens(5000L)
            .maxModelCalls(10)
            .maxToolCalls(20)
            .send()
            .join()
            .getAgentInstanceKey();

    camundaClient
        .newUpdateAgentInstanceCommand(agentInstanceKey2)
        .elementInstanceKey(ei2)
        .status(AgentInstanceUpdateStatus.THINKING)
        .tools(
            List.of(
                AgentTool.of("search", "Search the web", "searchTask"), AgentTool.of("summarize")))
        .inputTokens(150L)
        .outputTokens(300L)
        .modelCalls(3)
        .toolCalls(2)
        .send()
        .join();

    // Wait until both are indexed
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey1);
    waitForAgentInstanceWithStatusToBeIndexed(
        camundaClient, agentInstanceKey2, AgentInstanceStatus.THINKING);
  }

  @Test
  void shouldGetAgentInstanceWithRequiredProperties() {
    // when
    final var response = camundaClient.newAgentInstanceGetRequest(agentInstanceKey1).execute();

    // then — verifies identity and engine-derived properties
    assertSoftly(
        softly -> {
          softly
              .assertThat(response.getAgentInstanceKey())
              .as("agentInstanceKey")
              .isEqualTo(agentInstanceKey1);
          softly.assertThat(response.getElementId()).as("elementId").isEqualTo(AGENT_ELEMENT_ID);
          softly
              .assertThat(response.getProcessInstanceKey())
              .as("processInstanceKey")
              .isEqualTo(processInstanceKey1);
          softly
              .assertThat(response.getRootProcessInstanceKey())
              .as("rootProcessInstanceKey")
              .isEqualTo(processInstanceKey1);
          softly
              .assertThat(response.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo(bpmnProcessId);
          softly
              .assertThat(response.getProcessDefinitionVersion())
              .as("processDefinitionVersion")
              .isGreaterThan(0);
          softly
              .assertThat(response.getProcessDefinitionVersionTag())
              .as("processDefinitionVersionTag")
              .isNull();
          softly.assertThat(response.getTenantId()).as("tenantId").isNotNull();
          softly.assertThat(response.getCreationDate()).as("creationDate").isNotNull();
          softly.assertThat(response.getLastUpdatedDate()).as("lastUpdatedDate").isNotNull();
          softly.assertThat(response.getCompletionDate()).as("completionDate").isNull();

          final var definition = response.getDefinition();
          softly.assertThat(definition.getModel()).as("definition.model").isEqualTo("gpt-4o");
          softly.assertThat(definition.getProvider()).as("definition.provider").isEqualTo("openai");
          softly
              .assertThat(definition.getSystemPrompt())
              .as("definition.systemPrompt")
              .isEqualTo("You are a helpful assistant.");

          final var metrics = response.getMetrics();
          softly.assertThat(metrics).as("metrics").isNotNull();
          softly.assertThat(metrics.getInputTokens()).as("metrics.inputTokens").isEqualTo(0L);
          softly.assertThat(metrics.getOutputTokens()).as("metrics.outputTokens").isEqualTo(0L);
          softly.assertThat(metrics.getModelCalls()).as("metrics.modelCalls").isEqualTo(0);
          softly.assertThat(metrics.getToolCalls()).as("metrics.toolCalls").isEqualTo(0);

          softly.assertThat(response.getTools()).as("tools").isEmpty();
        });
  }

  @Test
  void shouldGetAgentInstanceWithAllProperties() {
    // when
    final var response = camundaClient.newAgentInstanceGetRequest(agentInstanceKey2).execute();

    // then — verifies all user-provided (CREATE + UPDATE) and engine-derived properties
    assertSoftly(
        softly -> {
          softly
              .assertThat(response.getAgentInstanceKey())
              .as("agentInstanceKey")
              .isEqualTo(agentInstanceKey2);
          softly.assertThat(response.getElementId()).as("elementId").isEqualTo(AGENT_ELEMENT_ID);
          softly
              .assertThat(response.getProcessInstanceKey())
              .as("processInstanceKey")
              .isEqualTo(processInstanceKey2);
          softly
              .assertThat(response.getRootProcessInstanceKey())
              .as("rootProcessInstanceKey")
              .isEqualTo(processInstanceKey2);
          softly
              .assertThat(response.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo(bpmnProcessId);
          softly.assertThat(response.getTenantId()).as("tenantId").isNotNull();
          softly.assertThat(response.getCreationDate()).as("creationDate").isNotNull();
          softly.assertThat(response.getLastUpdatedDate()).as("lastUpdatedDate").isNotNull();
          softly.assertThat(response.getCompletionDate()).as("completionDate").isNull();

          final var definition = response.getDefinition();
          softly.assertThat(definition.getModel()).as("definition.model").isEqualTo("gpt-4o");
          softly.assertThat(definition.getProvider()).as("definition.provider").isEqualTo("openai");
          softly
              .assertThat(definition.getSystemPrompt())
              .as("definition.systemPrompt")
              .isEqualTo("You are a helpful assistant.");

          final var limits = response.getLimits();
          softly.assertThat(limits.getMaxTokens()).as("limits.maxTokens").isEqualTo(5000L);
          softly.assertThat(limits.getMaxModelCalls()).as("limits.maxModelCalls").isEqualTo(10);
          softly.assertThat(limits.getMaxToolCalls()).as("limits.maxToolCalls").isEqualTo(20);

          final var metrics = response.getMetrics();
          softly.assertThat(metrics.getInputTokens()).as("metrics.inputTokens").isEqualTo(150L);
          softly.assertThat(metrics.getOutputTokens()).as("metrics.outputTokens").isEqualTo(300L);
          softly.assertThat(metrics.getModelCalls()).as("metrics.modelCalls").isEqualTo(3);
          softly.assertThat(metrics.getToolCalls()).as("metrics.toolCalls").isEqualTo(2);

          final var tools = response.getTools();
          softly.assertThat(tools).as("tools").hasSize(2);
          softly
              .assertThat(tools)
              .as("tools")
              .extracting(Tool::getName, Tool::getElementId, Tool::getDescription)
              .containsExactly(
                  tuple("search", "searchTask", "Search the web"), tuple("summarize", null, null));
        });
  }

  @Test
  void shouldReturnNotFoundForUnknownKey() {
    // when
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(() -> camundaClient.newAgentInstanceGetRequest(Long.MAX_VALUE).execute())
            .actual();

    // then
    assertThat(problemException.code()).isEqualTo(404);
    final ProblemDetail details = problemException.details();
    assertThat(details.getDetail())
        .contains("Agent Instance with key '%d' not found".formatted(Long.MAX_VALUE));
  }
}
