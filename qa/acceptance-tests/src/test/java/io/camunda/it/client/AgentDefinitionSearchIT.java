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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.response.AgentDefinition;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class AgentDefinitionSearchIT {

  private static final String SUB_PROCESS_ELEMENT_ID = "searchSubProcessAgent";
  private static final String TASK_ELEMENT_ID = "searchTaskAgent";
  private static final String EXTERNAL_ELEMENT_ID = "searchExternalAgent";
  private static final String SUB_PROCESS_NAME = "Search Agent";

  private static CamundaClient camundaClient;

  // agentDefinition1v1 / agentDefinition1v2: same process id + elementId, two versions
  private static long processDefinitionKey1v1;
  private static long processDefinitionKey1v2;
  private static String processDefinitionId1;
  private static long agentDefinitionKey1v1;
  private static long agentDefinitionKey1v2;

  // agentDefinition2: AI_AGENT_TASK, no explicit name
  private static long processDefinitionKey2;
  private static String processDefinitionId2;
  private static long agentDefinitionKey2;

  // agentDefinition3: EXTERNAL_AGENT
  private static long processDefinitionKey3;
  private static long agentDefinitionKey3;

  @BeforeAll
  static void setup() {
    // process1 v1 — AI_AGENT_SUB_PROCESS, versionTag "v1"
    final var process1v1 =
        deployProcessAndWaitForIt(
            camundaClient,
            subProcessAgentModel(
                "AgentDefinitionSearchProcess1", "search-sub-process-job-v1", "v1"),
            "agent-definition-search-1-v1.bpmn");
    processDefinitionKey1v1 = process1v1.getProcessDefinitionKey();
    processDefinitionId1 = process1v1.getBpmnProcessId();

    // process1 v2 — same process id + elementId, new version, versionTag "v2"
    final var process1v2 =
        deployProcessAndWaitForIt(
            camundaClient,
            subProcessAgentModel(processDefinitionId1, "search-sub-process-job-v2", "v2"),
            "agent-definition-search-1-v2.bpmn");
    processDefinitionKey1v2 = process1v2.getProcessDefinitionKey();

    // process2 — AI_AGENT_TASK, no explicit name
    final var process2Model =
        Bpmn.createExecutableProcess("AgentDefinitionSearchProcess2")
            .startEvent()
            .serviceTask(
                TASK_ELEMENT_ID,
                t -> t.zeebeJobType("search-task-job").zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();
    final var process2 =
        deployProcessAndWaitForIt(camundaClient, process2Model, "agent-definition-search-2.bpmn");
    processDefinitionKey2 = process2.getProcessDefinitionKey();
    processDefinitionId2 = process2.getBpmnProcessId();

    // process3 — EXTERNAL_AGENT
    final var process3Model =
        Bpmn.createExecutableProcess("AgentDefinitionSearchProcess3")
            .startEvent()
            .serviceTask(
                EXTERNAL_ELEMENT_ID,
                t -> t.zeebeJobType("search-external-job").zeebeExternalAgentDefinition())
            .endEvent()
            .done();
    final var process3 =
        deployProcessAndWaitForIt(camundaClient, process3Model, "agent-definition-search-3.bpmn");
    processDefinitionKey3 = process3.getProcessDefinitionKey();

    waitForAgentDefinitionsToBeIndexed(
        camundaClient, f -> f.processDefinitionId(processDefinitionId1), 2);
    waitForAgentDefinitionsToBeIndexed(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey2), 1);
    waitForAgentDefinitionsToBeIndexed(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey3), 1);

    agentDefinitionKey1v1 = fetchAgentDefinitionKey(processDefinitionKey1v1);
    agentDefinitionKey1v2 = fetchAgentDefinitionKey(processDefinitionKey1v2);
    agentDefinitionKey2 = fetchAgentDefinitionKey(processDefinitionKey2);
    agentDefinitionKey3 = fetchAgentDefinitionKey(processDefinitionKey3);
  }

  private static io.camunda.zeebe.model.bpmn.BpmnModelInstance subProcessAgentModel(
      final String processId, final String jobType, final String versionTag) {
    return Bpmn.createExecutableProcess(processId)
        .versionTag(versionTag)
        .startEvent()
        .adHocSubProcess(
            SUB_PROCESS_ELEMENT_ID,
            ahsp ->
                ahsp.name(SUB_PROCESS_NAME)
                    .zeebeJobType(jobType)
                    .zeebeAiAgentSubProcessDefinition()
                    .task("inner"))
        .endEvent()
        .done();
  }

  private static long fetchAgentDefinitionKey(final long processDefinitionKey) {
    return camundaClient
        .newAgentDefinitionSearchRequest()
        .filter(f -> f.processDefinitionKey(processDefinitionKey))
        .execute()
        .items()
        .getFirst()
        .getAgentDefinitionKey();
  }

  @Test
  void shouldSearchReturnAgentDefinitions() {
    // when
    final var response = camundaClient.newAgentDefinitionSearchRequest().execute();

    // then
    assertThat(response.items())
        .as("search with no filter should return every deployed agent definition")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .contains(
            agentDefinitionKey1v1, agentDefinitionKey1v2, agentDefinitionKey2, agentDefinitionKey3);
  }

  @Test
  void shouldFilterByAgentDefinitionKey() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.agentDefinitionKey(agentDefinitionKey2))
            .execute();

    // then
    assertThat(response.items())
        .as("agentDefinitionKey filter should return only the matching definition")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey2));
  }

  @Test
  void shouldFilterByAgentType() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.agentType(AgentDefinitionType.EXTERNAL_AGENT))
            .execute();

    // then
    assertThat(response.items())
        .as("agentType filter should return only the EXTERNAL_AGENT definition")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey3));
  }

  @Test
  void shouldFilterByAgentTypeWithAdvancedOperators() {
    // when — notIn excludes AI_AGENT_SUB_PROCESS and AI_AGENT_TASK, leaving only the external agent
    final var notInResponse =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(
                f ->
                    f.agentType(
                        b ->
                            b.notIn(
                                AgentDefinitionType.AI_AGENT_SUB_PROCESS,
                                AgentDefinitionType.AI_AGENT_TASK)))
            .execute();

    // then
    assertThat(notInResponse.items())
        .as("agentType $notIn should exclude sub-process and task types, leaving the external one")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .containsExactly(agentDefinitionKey3);
  }

  @Test
  void shouldFilterByName() {
    // when — both versions of process1 share the same explicit BPMN name
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.name(SUB_PROCESS_NAME))
            .execute();

    // then
    assertThat(response.items())
        .as("name filter should match both process versions sharing the same BPMN name")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .containsExactlyInAnyOrder(agentDefinitionKey1v1, agentDefinitionKey1v2);
  }

  @Test
  void shouldFilterByElementId() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.elementId(TASK_ELEMENT_ID))
            .execute();

    // then
    assertThat(response.items())
        .as("elementId filter should return only the matching definition")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey2));
  }

  @Test
  void shouldFilterByProcessDefinitionId() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId2))
            .execute();

    // then
    assertThat(response.items())
        .as("processDefinitionId filter should return only definitions owned by that process")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey2));
  }

  @Test
  void shouldListAcrossProcessDefinitionVersions() {
    // when — filtering by processDefinitionId + elementId, omitting processDefinitionKey, is how
    // the same agent element is listed across all of its process definition's versions
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(
                f -> f.processDefinitionId(processDefinitionId1).elementId(SUB_PROCESS_ELEMENT_ID))
            .execute();

    // then
    assertThat(response.items())
        .as(
            "filtering by processDefinitionId + elementId should list the same agent element"
                + " across all of its process definition's versions")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .containsExactlyInAnyOrder(agentDefinitionKey1v1, agentDefinitionKey1v2);
    assertThat(response.items())
        .as("the listed definitions should span both deployed process definition versions")
        .extracting(AgentDefinition::getProcessDefinitionVersion)
        .containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void shouldFilterByProcessDefinitionKey() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey1v2))
            .execute();

    // then
    assertThat(response.items())
        .as("processDefinitionKey filter should return only that exact version's definition")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey1v2));
  }

  @Test
  void shouldFilterByProcessDefinitionVersion() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId1).processDefinitionVersion(2))
            .execute();

    // then
    assertThat(response.items())
        .as("processDefinitionVersion filter should return only the matching version")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey1v2));
  }

  @Test
  void shouldFilterByProcessDefinitionVersionTag() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionVersionTag("v1"))
            .execute();

    // then
    assertThat(response.items())
        .as("processDefinitionVersionTag filter should return only the matching version")
        .singleElement()
        .satisfies(ad -> assertThat(ad.getAgentDefinitionKey()).isEqualTo(agentDefinitionKey1v1));
  }

  @Test
  void shouldFilterByTenantId() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.tenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .execute();

    // then
    assertThat(response.items())
        .as("tenantId filter should return every agent definition owned by the default tenant")
        .extracting(AgentDefinition::getAgentDefinitionKey, AgentDefinition::getTenantId)
        .contains(
            tuple(agentDefinitionKey1v1, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            tuple(agentDefinitionKey1v2, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            tuple(agentDefinitionKey2, TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            tuple(agentDefinitionKey3, TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  void shouldReturnEmptyForUnknownTenant() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.tenantId("unknown-tenant"))
            .execute();

    // then
    assertThat(response.items())
        .as("an unknown tenant should match no agent definitions")
        .isEmpty();
  }

  @Test
  void shouldSortByAgentDefinitionKeyAscending() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .sort(s -> s.agentDefinitionKey().asc())
            .execute();

    // then
    assertThat(response.items())
        .as("results should be ordered by ascending agentDefinitionKey")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .isSorted();
  }

  @Test
  void shouldSortByProcessDefinitionVersionAscending() {
    // when
    final var response =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId1))
            .sort(s -> s.processDefinitionVersion().asc())
            .execute();

    // then
    assertThat(response.items())
        .as("results should be ordered by ascending processDefinitionVersion, v1 before v2")
        .extracting(AgentDefinition::getAgentDefinitionKey)
        .containsExactly(agentDefinitionKey1v1, agentDefinitionKey1v2);
  }

  @Test
  void shouldPaginateResults() {
    // when — process1 has 2 agent definition versions; request them one at a time
    final var page1 =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId1))
            .sort(s -> s.processDefinitionVersion().asc())
            .page(p -> p.limit(1))
            .execute();

    assertThat(page1.items()).as("first page should return exactly the requested limit").hasSize(1);

    final var page2 =
        camundaClient
            .newAgentDefinitionSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId1))
            .sort(s -> s.processDefinitionVersion().asc())
            .page(p -> p.limit(1).from(1))
            .execute();

    // then — page1 + page2 cover both versions without overlap
    assertThat(page2.items())
        .as("second page should return exactly the requested limit")
        .hasSize(1);
    final var page1Keys =
        page1.items().stream().map(AgentDefinition::getAgentDefinitionKey).toList();
    final var page2Keys =
        page2.items().stream().map(AgentDefinition::getAgentDefinitionKey).toList();
    assertThat(page1Keys)
        .as("paginated pages should not overlap")
        .doesNotContainAnyElementsOf(page2Keys);
  }
}
