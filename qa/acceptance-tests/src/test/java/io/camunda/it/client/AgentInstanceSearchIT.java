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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class AgentInstanceSearchIT {

  private static final String AGENT_ELEMENT_ID = "agentAhsp";

  private static CamundaClient camundaClient;

  // Three separate process instances so each CREATE targets a unique elementInstanceKey.
  // ai1 + ai2 belong to processDefinition1; ai3 belongs to processDefinition2.
  private static long agentInstanceKey1;
  private static long agentInstanceKey2;
  private static long agentInstanceKey3;
  private static long processInstanceKey1;
  private static long processDefinitionKey;
  private static String processDefinitionId;
  private static long processDefinitionKey2;
  private static String processDefinitionId2;

  @BeforeAll
  static void setup() {
    // Process definition 1 — ai1 (CREATE + UPDATE) and ai2 (CREATE only) belong here.
    final var processModel1 =
        Bpmn.createExecutableProcess("AgentInstanceSearchProcess1")
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .moveToActivity(AGENT_ELEMENT_ID)
            .endEvent("end")
            .done();

    final var process1 =
        deployProcessAndWaitForIt(camundaClient, processModel1, "agent-search-1.bpmn");
    processDefinitionKey = process1.getProcessDefinitionKey();
    processDefinitionId = process1.getBpmnProcessId();

    // Process definition 2 — ai3 (CREATE only) belongs here.
    final var processModel2 =
        Bpmn.createExecutableProcess("AgentInstanceSearchProcess2")
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .moveToActivity(AGENT_ELEMENT_ID)
            .endEvent("end")
            .done();

    final var process2 =
        deployProcessAndWaitForIt(camundaClient, processModel2, "agent-search-2.bpmn");
    processDefinitionKey2 = process2.getProcessDefinitionKey();
    processDefinitionId2 = process2.getBpmnProcessId();

    // — pi1 → ei1 → ai1: CREATE + UPDATE (THINKING) —
    final var pi1 = startProcessInstance(camundaClient, processDefinitionId);
    processInstanceKey1 = pi1.getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey1),
        1);
    final long ei1 =
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
            .systemPrompt("You are a search assistant.")
            .send()
            .join()
            .getAgentInstanceKey();

    camundaClient
        .newUpdateAgentInstanceCommand(agentInstanceKey1)
        .elementInstanceKey(ei1)
        .status(AgentInstanceUpdateStatus.THINKING)
        .send()
        .join();

    // — pi2 → ei2 → ai2: CREATE only (INITIALIZING) —
    final var pi2 = startProcessInstance(camundaClient, processDefinitionId);
    final long processInstanceKey2 = pi2.getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey2),
        1);
    final long ei2 =
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
            .model("claude-3-5-sonnet")
            .provider("anthropic")
            .systemPrompt("You are a code reviewer.")
            .send()
            .join()
            .getAgentInstanceKey();

    // — pi3 → ei3 → ai3: CREATE only (INITIALIZING), from processDefinition2 —
    final var pi3 = startProcessInstance(camundaClient, processDefinitionId2);
    final long processInstanceKey3 = pi3.getProcessInstanceKey();
    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey3),
        1);
    final long ei3 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey3))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    agentInstanceKey3 =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ei3)
            .model("gpt-4o-mini")
            .provider("openai")
            .systemPrompt("You are a summarizer.")
            .send()
            .join()
            .getAgentInstanceKey();

    // Wait until all are indexed
    waitForAgentInstanceWithStatusToBeIndexed(
        camundaClient, agentInstanceKey1, AgentInstanceStatus.THINKING);
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey2);
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey3);
  }

  @Test
  void shouldSearchReturnAgentInstances() {
    // when
    final var response = camundaClient.newAgentInstanceSearchRequest().execute();

    // then
    assertThat(response.items())
        .extracting(AgentInstance::getAgentInstanceKey)
        .containsExactlyInAnyOrder(agentInstanceKey1, agentInstanceKey2, agentInstanceKey3);
  }

  @Test
  void shouldFilterByAgentInstanceKey() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.agentInstanceKey(agentInstanceKey2))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .satisfies(ai -> assertThat(ai.getAgentInstanceKey()).isEqualTo(agentInstanceKey2));
  }

  @Test
  void shouldFilterByStatus() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(
                f ->
                    f.processDefinitionKey(processDefinitionKey)
                        .status(AgentInstanceStatus.THINKING))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .satisfies(
            ai -> {
              assertThat(ai.getAgentInstanceKey()).isEqualTo(agentInstanceKey1);
              assertThat(ai.getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
            });
  }

  @Test
  void shouldFilterByProcessInstanceKey() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey1))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .satisfies(
            ai -> {
              assertThat(ai.getAgentInstanceKey()).isEqualTo(agentInstanceKey1);
              assertThat(ai.getProcessInstanceKey()).isEqualTo(processInstanceKey1);
            });
  }

  @Test
  void shouldFilterByProcessDefinitionKey() {
    // when — pd1 has 2 agent instances
    final var responsePd1 =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .execute();

    // then
    assertThat(responsePd1.items())
        .extracting(AgentInstance::getAgentInstanceKey)
        .containsExactlyInAnyOrder(agentInstanceKey1, agentInstanceKey2);

    // when — pd2 has 1 agent instance
    final var responsePd2 =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey2))
            .execute();

    // then
    assertThat(responsePd2.items())
        .singleElement()
        .satisfies(ai -> assertThat(ai.getAgentInstanceKey()).isEqualTo(agentInstanceKey3));
  }

  @Test
  void shouldFilterByProcessDefinitionId() {
    // when — pd1 has 2 agent instances
    final var responsePd1 =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId))
            .execute();

    // then
    assertThat(responsePd1.items())
        .extracting(AgentInstance::getAgentInstanceKey)
        .containsExactlyInAnyOrder(agentInstanceKey1, agentInstanceKey2);

    // when — pd2 has 1 agent instance
    final var responsePd2 =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId2))
            .execute();

    // then
    assertThat(responsePd2.items())
        .singleElement()
        .satisfies(ai -> assertThat(ai.getAgentInstanceKey()).isEqualTo(agentInstanceKey3));
  }

  @Test
  void shouldFilterByTenantId() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.tenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .execute();

    // then
    assertThat(response.items())
        .extracting(AgentInstance::getAgentInstanceKey, AgentInstance::getTenantId)
        .containsExactlyInAnyOrder(
            tuple(agentInstanceKey1, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            tuple(agentInstanceKey2, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            tuple(agentInstanceKey3, TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  void shouldReturnEmptyForUnknownTenant() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.tenantId("unknown-tenant"))
            .execute();

    // then
    assertThat(response.items()).isEmpty();
  }

  @Test
  void shouldSortByCreationDateDescending() {
    // when
    final var response =
        camundaClient.newAgentInstanceSearchRequest().sort(s -> s.creationDate().desc()).execute();

    // then — ai3 was created last, so it should appear before ai2 and ai1
    assertThat(response.items())
        .extracting(AgentInstance::getAgentInstanceKey)
        .containsExactly(agentInstanceKey3, agentInstanceKey2, agentInstanceKey1);
  }

  @Test
  void shouldSortByStatusAscending() {
    // when
    final var response =
        camundaClient.newAgentInstanceSearchRequest().sort(s -> s.status().asc()).execute();

    // then
    assertThat(response.items())
        .extracting(AgentInstance::getStatus)
        .containsExactly(
            AgentInstanceStatus.INITIALIZING,
            AgentInstanceStatus.INITIALIZING,
            AgentInstanceStatus.THINKING);
  }

  @Test
  void shouldPaginateResults() {
    // when — pd1 has 2 agent instances; request them one at a time
    final var page1 =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .sort(s -> s.creationDate().asc())
            .page(p -> p.limit(1))
            .execute();

    assertThat(page1.items()).hasSize(1);

    final var page2 =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .sort(s -> s.creationDate().asc())
            .page(p -> p.limit(1).from(1))
            .execute();

    // then — page1 + page2 cover all 2 agent instances in pd1 without overlap
    assertThat(page2.items()).hasSize(1);
    final var page1Keys = page1.items().stream().map(AgentInstance::getAgentInstanceKey).toList();
    final var page2Keys = page2.items().stream().map(AgentInstance::getAgentInstanceKey).toList();
    assertThat(page1Keys).doesNotContainAnyElementsOf(page2Keys);
  }
}
