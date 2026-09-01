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
import io.camunda.client.api.command.AgentInstanceHistoryContent.ObjectContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.TextContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.filter.AgentInstanceHistoryFilter;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class AgentInstanceHistorySearchIT {

  private static final String AGENT_ELEMENT_ID = "agentHistorySearchElement";
  private static final String PROCESS_ID = "agentHistorySearchProcess";
  private static final String AGENT_JOB_TYPE = "agent-task";

  private static CamundaClient camundaClient;

  private static long agentInstanceKey;
  private static long elementInstanceKey;
  private static long jobKey;
  private static String jobLease;
  private static long historyItemKey0;
  private static long historyItemKey1;
  private static long historyItemKey2;
  private static long historyItemKey3;

  @BeforeAll
  static void setup() {
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
            .zeebeJobType(AGENT_JOB_TYPE)
            .zeebeAiAgentSubProcessDefinition()
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

    final var activatedJobs =
        camundaClient
            .newActivateJobsCommand()
            .jobType(AGENT_JOB_TYPE)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();
    assertThat(activatedJobs)
        .as("expected to activate one agent job for process instance %d", processInstanceKey)
        .isNotEmpty();
    jobKey = activatedJobs.get(0).getKey();
    jobLease = activatedJobs.get(0).getLeaseToken();

    // The initial CONFIGURATION history item establishing model/provider/systemPrompt is
    // produced before the USER/ASSISTANT/TOOL_RESULT items added below, so give it an earlier
    // timestamp to keep producedAt-ascending ordering deterministic across all 4 items.
    final var createResponse =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .jobKey(jobKey)
            .jobLease(jobLease)
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(List.of(AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T09:59:00Z"))
                        .model("gpt-4o")
                        .provider("openai")
                        .systemPrompt(
                            List.of(
                                AgentInstanceHistoryContent.text("You are a helpful assistant.")))))
            .send()
            .join();
    agentInstanceKey = createResponse.getAgentInstanceKey();
    historyItemKey0 = createResponse.getCreatedHistory().getFirst().getHistoryItemKey();

    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);

    // Create 3 history items with different roles
    final var createdHistory =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(jobKey)
            .jobLease(jobLease)
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.USER)
                        .content(
                            List.of(AgentInstanceHistoryContent.text("Hello, what can you do?")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:00:00Z")),
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.ASSISTANT)
                        .content(
                            List.of(
                                AgentInstanceHistoryContent.text("I can help with many tasks.")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:01:00Z"))
                        .metrics(
                            new AgentInstanceHistoryMetrics()
                                .inputTokens(512L)
                                .outputTokens(148L)
                                .durationMs(1200L)),
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.TOOL_RESULT)
                        .content(
                            List.of(
                                AgentInstanceHistoryContent.object(
                                    Arrays.asList(Map.of("id", 1), Map.of("id", 2))),
                                AgentInstanceHistoryContent.object(Arrays.asList(10, 20, 30)),
                                AgentInstanceHistoryContent.object(42),
                                AgentInstanceHistoryContent.object(true),
                                AgentInstanceHistoryContent.object("search-complete")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:02:00Z"))))
            .send()
            .join()
            .getCreatedHistory();

    historyItemKey1 = createdHistory.get(0).getHistoryItemKey();
    historyItemKey2 = createdHistory.get(1).getHistoryItemKey();
    historyItemKey3 = createdHistory.get(2).getHistoryItemKey();

    // Complete the job so JobCompleteProcessor emits AGENT_HISTORY:COMMIT,
    // causing history items to transition to COMMITTED and become searchable.
    camundaClient.newCompleteCommand(jobKey).withLeaseToken(jobLease).execute();

    waitForHistoryItemsToBeIndexed(camundaClient, agentInstanceKey, 4);
  }

  @Test
  void shouldReturnAllHistoryItems() {
    // when
    final var response =
        camundaClient.newAgentInstanceHistorySearchRequest(agentInstanceKey).execute();

    // then
    assertThat(response.items())
        .extracting(AgentInstanceHistory::getHistoryItemKey)
        .containsExactlyInAnyOrder(
            historyItemKey0, historyItemKey1, historyItemKey2, historyItemKey3);
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
              assertThat(item.getCommitStatus()).isNotNull();
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

    // then — items should come back in producedAt order: item0 < item1 < item2 < item3
    assertThat(response.items())
        .extracting(AgentInstanceHistory::getHistoryItemKey)
        .containsExactly(historyItemKey0, historyItemKey1, historyItemKey2, historyItemKey3);
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

  @Test
  void shouldReturnObjectContentForAllJsonValueTypes() {
    // when
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .filter(f -> f.historyItemKey(historyItemKey3))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getContent())
                  .allSatisfy(c -> assertThat(c).isInstanceOf(ObjectContent.class));
              assertThat(item.getContent())
                  .extracting(c -> ((ObjectContent) c).getObject())
                  .containsExactly(
                      Arrays.asList(Map.of("id", 1), Map.of("id", 2)),
                      Arrays.asList(10, 20, 30),
                      42,
                      true,
                      "search-complete");
            });
  }

  @Test
  void shouldReturnNullMetricsWhenNotProvided() {
    // when — fetch USER and TOOL_RESULT items, both created without metrics
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .filter(f -> f.historyItemKey(p -> p.in(historyItemKey1, historyItemKey3)))
            .execute();

    // then
    assertThat(response.items())
        .hasSize(2)
        .as("USER and TOOL_RESULT items created without metrics must return null metrics")
        .extracting(AgentInstanceHistory::getMetrics)
        .containsOnlyNulls();
  }

  @Test
  void shouldReturnProvidedMetrics() {
    // when — fetch ASSISTANT item, created with explicit metrics
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .filter(f -> f.historyItemKey(historyItemKey2))
            .execute();

    // then
    assertThat(response.items())
        .singleElement()
        .extracting(AgentInstanceHistory::getMetrics)
        .satisfies(
            m -> {
              assertThat(m)
                  .as("ASSISTANT item created with metrics must return non-null metrics")
                  .isNotNull();
              assertThat(m.getInputTokens())
                  .as("inputTokens must match the value provided at creation")
                  .isEqualTo(512L);
              assertThat(m.getOutputTokens())
                  .as("outputTokens must match the value provided at creation")
                  .isEqualTo(148L);
              assertThat(m.getDurationMs())
                  .as("durationMs must match the value provided at creation")
                  .isEqualTo(1200L);
            });
  }

  @Test
  void shouldCreateAgentInstanceWithMultipleHistoryItemsInOneBatch() {
    // given - an agent job for which we can create an agent instance
    final var job = startAndActivateAgentJob();

    // when — a CONFIGURATION item and a USER item are submitted together in ONE CREATE call,
    // proving CREATE can batch more than a single history item at a time.
    final var createResponse =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(job.elementInstanceKey())
            .jobKey(job.jobKey())
            .jobLease(job.jobLease())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(List.of(AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T09:59:00Z"))
                        .model("gpt-4o")
                        .provider("openai")
                        .systemPrompt(
                            List.of(
                                AgentInstanceHistoryContent.text("You are a helpful assistant."))),
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.USER)
                        .content(List.of(AgentInstanceHistoryContent.text("Hello there")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:00:00Z"))))
            .send()
            .join();
    final long agentInstanceKey = createResponse.getAgentInstanceKey();
    final long configurationItemKey = createResponse.getCreatedHistory().get(0).getHistoryItemKey();
    final long userItemKey = createResponse.getCreatedHistory().get(1).getHistoryItemKey();

    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);

    camundaClient.newCompleteCommand(job.jobKey()).withLeaseToken(job.jobLease()).execute();
    waitForHistoryItemsToBeIndexed(camundaClient, agentInstanceKey, 2);

    // then
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .sort(s -> s.producedAt().desc())
            .execute();

    assertThat(response.items())
        .as("both items from the single CREATE batch must come back, ordered by producedAt")
        .extracting(AgentInstanceHistory::getHistoryItemKey)
        .containsExactly(userItemKey, configurationItemKey);
    assertThat(response.items())
        .as("both items from the single CREATE batch must come back, ordered by producedAt")
        .extracting(AgentInstanceHistory::getRole)
        .containsExactly(AgentInstanceHistoryRole.USER, AgentInstanceHistoryRole.CONFIGURATION);
    assertThat(response.items().getFirst().getContent())
        .as("the USER item's submitted content must be preserved")
        .extracting(c -> ((TextContent) c).getText())
        .containsExactly("Hello there");
  }

  @Test
  void shouldUpdateAgentInstanceWithMultipleHistoryItemsInOneBatch() {
    // given - an agent job for which we can create an agent instance
    final var job = startAndActivateAgentJob();

    final var createResponse =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(job.elementInstanceKey())
            .jobKey(job.jobKey())
            .jobLease(job.jobLease())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(List.of(AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T09:59:00Z"))
                        .model("gpt-4o")
                        .provider("openai")
                        .systemPrompt(
                            List.of(
                                AgentInstanceHistoryContent.text("You are a helpful assistant.")))))
            .send()
            .join();
    final long agentInstanceKey = createResponse.getAgentInstanceKey();
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);

    // when — three items of different roles are submitted together in ONE UPDATE call.
    final var updatedHistory =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(job.elementInstanceKey())
            .jobKey(job.jobKey())
            .jobLease(job.jobLease())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.USER)
                        .content(List.of(AgentInstanceHistoryContent.text("What's the weather?")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:00:00Z")),
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.ASSISTANT)
                        .content(List.of(AgentInstanceHistoryContent.text("Let me check that.")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:01:00Z"))
                        .metrics(
                            new AgentInstanceHistoryMetrics()
                                .inputTokens(256L)
                                .outputTokens(64L)
                                .durationMs(800L)),
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.TOOL_RESULT)
                        .content(List.of(AgentInstanceHistoryContent.object("sunny, 22C")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:02:00Z"))))
            .send()
            .join()
            .getCreatedHistory();
    final long userItemKey = updatedHistory.get(0).getHistoryItemKey();
    final long assistantItemKey = updatedHistory.get(1).getHistoryItemKey();
    final long toolResultItemKey = updatedHistory.get(2).getHistoryItemKey();

    // Complete the job so all three PENDING items commit and become searchable.
    camundaClient.newCompleteCommand(job.jobKey()).withLeaseToken(job.jobLease()).execute();
    waitForHistoryItemsToBeIndexed(
        camundaClient,
        agentInstanceKey,
        f -> f.role(r -> r.neq(AgentInstanceHistoryRole.CONFIGURATION)),
        3);

    // then
    final var response =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey)
            .filter(f -> f.role(r -> r.neq(AgentInstanceHistoryRole.CONFIGURATION)))
            .sort(s -> s.producedAt().asc())
            .execute();

    assertThat(response.items())
        .as("all three items from the single UPDATE batch must come back, ordered by producedAt")
        .extracting(AgentInstanceHistory::getHistoryItemKey)
        .containsExactly(userItemKey, assistantItemKey, toolResultItemKey);
    assertThat(response.items())
        .as("all three items from the single UPDATE batch must come back, ordered by producedAt")
        .extracting(AgentInstanceHistory::getRole)
        .containsExactly(
            AgentInstanceHistoryRole.USER,
            AgentInstanceHistoryRole.ASSISTANT,
            AgentInstanceHistoryRole.TOOL_RESULT);
    final var metrics = response.items().stream().map(AgentInstanceHistory::getMetrics).toList();
    assertThat(metrics.get(0)).as("USER item must have no metrics").isNull();
    assertThat(metrics.get(1))
        .as("ASSISTANT item must return the metrics submitted with it")
        .isNotNull();
    assertThat(metrics.get(1).getInputTokens()).isEqualTo(256L);
    assertThat(metrics.get(1).getOutputTokens()).isEqualTo(64L);
    assertThat(metrics.get(1).getDurationMs()).isEqualTo(800L);
    assertThat(metrics.get(2)).as("TOOL_RESULT item must have no metrics").isNull();
  }

  /**
   * Starts a new instance of the class's process and activates its agent job — but does not create
   * the agent instance itself, leaving each caller free to submit its own CREATE batch. Shared by
   * the two tests above so each can set up its own agent instance without touching the class's
   * shared {@code agentInstanceKey}/{@code elementInstanceKey} fields, which the other tests in
   * this class assert on.
   */
  private static AgentJob startAndActivateAgentJob() {
    final var pi = startProcessInstance(camundaClient, PROCESS_ID);
    final long processInstanceKey = pi.getProcessInstanceKey();

    waitForElementInstances(
        camundaClient,
        f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey),
        1);

    final long elementInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(AGENT_ELEMENT_ID).processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    final var activatedJobs =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();
    assertThat(activatedJobs)
        .as("expected to activate one agent job for process instance %d", processInstanceKey)
        .isNotEmpty();

    return new AgentJob(
        elementInstanceKey, activatedJobs.get(0).getKey(), activatedJobs.get(0).getLeaseToken());
  }

  private static void waitForHistoryItemsToBeIndexed(
      final CamundaClient client, final long agentKey, final int expectedCount) {
    waitForHistoryItemsToBeIndexed(client, agentKey, f -> {}, expectedCount);
  }

  private static void waitForHistoryItemsToBeIndexed(
      final CamundaClient client,
      final long agentKey,
      final Consumer<AgentInstanceHistoryFilter> filter,
      final int expectedCount) {
    Awaitility.await("agent history indexed")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  client.newAgentInstanceHistorySearchRequest(agentKey).filter(filter).execute();
              assertThat(response.items()).hasSizeGreaterThanOrEqualTo(expectedCount);
            });
  }

  private record AgentJob(long elementInstanceKey, long jobKey, String jobLease) {}
}
