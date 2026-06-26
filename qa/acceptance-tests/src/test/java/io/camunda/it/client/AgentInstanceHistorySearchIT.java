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
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
public class AgentInstanceHistorySearchIT {

  private static final String AGENT_ELEMENT_ID = "agentHistorySearchElement";
  private static final String PROCESS_ID = "agentHistorySearchProcess";

  private static CamundaClient camundaClient;

  private static long agentInstanceKey;
  private static long elementInstanceKey;
  private static long jobKey;
  private static long historyItemKey1;
  private static long historyItemKey2;
  private static long historyItemKey3;

  @BeforeAll
  static void setup() {
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .endEvent("end")
            .done();

    final var process =
        deployProcessAndWaitForIt(camundaClient, processModel, "agent-history-search.bpmn");

    final var pi = startProcessInstance(camundaClient, process.getBpmnProcessId());
    final long processInstanceKey = pi.getProcessInstanceKey();

    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey),
        1);

    elementInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    agentInstanceKey =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .send()
            .join()
            .getAgentInstanceKey();

    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);

    final var activatedJobs =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .maxJobsToActivate(1)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();
    assertThat(activatedJobs)
        .as("expected to activate one agent job for process instance %d", processInstanceKey)
        .isNotEmpty();
    jobKey = activatedJobs.get(0).getKey();

    // Create 3 history items with different roles
    historyItemKey1 =
        camundaClient
            .newCreateAgentHistoryItemCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(jobKey)
            .role(AgentInstanceHistoryRole.USER)
            .content(List.of(AgentInstanceHistoryContent.text("Hello, what can you do?")))
            .producedAt(OffsetDateTime.parse("2025-06-01T10:00:00Z"))
            .send()
            .join()
            .getHistoryItemKey();

    historyItemKey2 =
        camundaClient
            .newCreateAgentHistoryItemCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(jobKey)
            .role(AgentInstanceHistoryRole.ASSISTANT)
            .content(List.of(AgentInstanceHistoryContent.text("I can help with many tasks.")))
            .producedAt(OffsetDateTime.parse("2025-06-01T10:01:00Z"))
            .send()
            .join()
            .getHistoryItemKey();

    historyItemKey3 =
        camundaClient
            .newCreateAgentHistoryItemCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(jobKey)
            .role(AgentInstanceHistoryRole.TOOL_RESULT)
            .content(List.of(AgentInstanceHistoryContent.text("Search results: ...")))
            .producedAt(OffsetDateTime.parse("2025-06-01T10:02:00Z"))
            .send()
            .join()
            .getHistoryItemKey();

    waitForHistoryItemsToBeIndexed(camundaClient, agentInstanceKey, 3);
  }

  @Test
  void shouldReturnAllHistoryItems() {
    // when
    final var response =
        camundaClient.newAgentInstanceHistorySearchRequest(agentInstanceKey).execute();

    // then
    assertThat(response.items())
        .extracting(AgentInstanceHistory::getHistoryItemKey)
        .containsExactlyInAnyOrder(historyItemKey1, historyItemKey2, historyItemKey3);
  }

  @Test
  void shouldFilterByRole() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .filter(f -> f.role(AgentInstanceHistoryRole.USER))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getHistoryItemKey()).isEqualTo(historyItemKey1);
              assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRole.USER);
            });
  }

  @Test
  void shouldFilterByHistoryItemKey() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .filter(f -> f.historyItemKey(historyItemKey1))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .satisfies(item -> assertThat(item.getHistoryItemKey()).isEqualTo(historyItemKey1));
  }

  @Test
  void shouldSortByProducedAtAscending() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .sort(s -> s.producedAt().asc())
            .execute();

    // then — items should come back in producedAt order: item1 < item2 < item3
    assertThat(response.items())
        .extracting(AgentInstanceHistory::getHistoryItemKey)
        .containsExactly(historyItemKey1, historyItemKey2, historyItemKey3);
  }

  @Test
  void shouldPaginateResults() {
    // when — page 1: limit 1
    final var page1 =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .sort(s -> s.producedAt().asc())
            .page(p -> p.limit(1))
            .execute();

    assertThat(page1.items()).hasSize(1);

    // when — page 2: limit 1, from 1
    final var page2 =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .sort(s -> s.producedAt().asc())
            .page(p -> p.limit(1).from(1))
            .execute();

    assertThat(page2.items()).hasSize(1);

    // then — pages do not overlap
    final var page1Keys =
        page1.items().stream().map(AgentInstanceHistory::getHistoryItemKey).toList();
    final var page2Keys =
        page2.items().stream().map(AgentInstanceHistory::getHistoryItemKey).toList();
    assertThat(page1Keys).doesNotContainAnyElementsOf(page2Keys);
  }

  private static void waitForHistoryItemsToBeIndexed(
      final CamundaClient client, final long agentKey, final int expectedCount) {
    Awaitility.await("agent history indexed")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response = client.newAgentInstanceHistorySearchRequest(agentKey).execute();
              assertThat(response.items()).hasSizeGreaterThanOrEqualTo(expectedCount);
            });
  }
}
