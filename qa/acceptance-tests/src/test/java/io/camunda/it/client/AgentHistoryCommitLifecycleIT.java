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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.TextContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceLimits;
import io.camunda.client.api.command.AgentTool;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class AgentHistoryCommitLifecycleIT {

  private static final String SERVICE_TASK_ID = "agentTask";
  private static final String PROCESS_ID = "agentHistoryCommitLifecycleProcess";

  private static CamundaClient camundaClient;

  @Test
  void shouldTransitionHistoryItemsFromPendingToCommittedOnJobCompletion() {
    // --- setup: two PENDING history items on an activated agentic job ---
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final long agentInstanceKey = createAgentInstance(elementInstanceKey);
    final var activatedJob = activateAgenticJob(processInstanceKey, true);
    final long historyItemKey1 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.USER,
            "Hello, what can you do?",
            OffsetDateTime.parse("2025-06-01T10:00:00Z"),
            1);
    final long historyItemKey2 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.ASSISTANT,
            "I can help with many tasks.",
            OffsetDateTime.parse("2025-06-01T10:01:00Z"),
            1);
    awaitHistoryStatuses(
        agentInstanceKey,
        "history items indexed as PENDING",
        tuple(historyItemKey1, AgentInstanceHistoryCommitStatus.PENDING),
        tuple(historyItemKey2, AgentInstanceHistoryCommitStatus.PENDING));

    // --- complete the job → engine emits COMMIT → items become COMMITTED ---
    camundaClient.newCompleteCommand(activatedJob).execute();

    // --- verify items are COMMITTED after job completion ---
    awaitHistoryStatuses(
        agentInstanceKey,
        "history items transitioned to COMMITTED",
        tuple(historyItemKey1, AgentInstanceHistoryCommitStatus.COMMITTED),
        tuple(historyItemKey2, AgentInstanceHistoryCommitStatus.COMMITTED));
  }

  @Test
  void shouldTransitionHistoryItemsFromPendingToDiscardedOnJobCancellation() {
    // --- setup: two PENDING history items on an activated agentic job ---
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final long agentInstanceKey = createAgentInstance(elementInstanceKey);
    final var activatedJob = activateAgenticJob(processInstanceKey, true);
    final long historyItemKey1 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.USER,
            "Hello, what can you do?",
            OffsetDateTime.parse("2025-06-01T10:00:00Z"),
            1);
    final long historyItemKey2 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.ASSISTANT,
            "I can help with many tasks.",
            OffsetDateTime.parse("2025-06-01T10:01:00Z"),
            1);
    awaitHistoryStatuses(
        agentInstanceKey,
        "history items indexed as PENDING",
        tuple(historyItemKey1, AgentInstanceHistoryCommitStatus.PENDING),
        tuple(historyItemKey2, AgentInstanceHistoryCommitStatus.PENDING));

    // --- cancel the process instance → the agentic job is destroyed without completing → engine
    // emits DISCARD → items become DISCARDED instead of leaking as PENDING ---
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();

    // --- verify items are DISCARDED after cancellation ---
    awaitHistoryStatuses(
        agentInstanceKey,
        "history items transitioned to DISCARDED",
        tuple(historyItemKey1, AgentInstanceHistoryCommitStatus.DISCARDED),
        tuple(historyItemKey2, AgentInstanceHistoryCommitStatus.DISCARDED));
  }

  @Test
  void shouldDiscardSupersededActivationAndCommitWinningActivationOnJobCompletion() {
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final long agentInstanceKey = createAgentInstance(elementInstanceKey);

    // Activation 1 (superseded): activate with lease, create a history item, then fail the job.
    final var activation1 = activateAgenticJob(processInstanceKey, true);
    final long supersededItemKey =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activation1,
            AgentInstanceHistoryRole.USER,
            "Message from superseded activation",
            OffsetDateTime.parse("2025-06-01T10:00:00Z"),
            1);
    awaitHistoryStatuses(
        agentInstanceKey,
        "superseded item indexed as PENDING before fail",
        tuple(supersededItemKey, AgentInstanceHistoryCommitStatus.PENDING));

    camundaClient
        .newFailCommand(activation1.getKey())
        .retries(1)
        .withLeaseToken(activation1.getLeaseToken())
        .execute();

    // Activation 2 (winning): same job re-activated under a new lease.
    final var activation2 = activateAgenticJob(processInstanceKey, true);
    assertThat(activation2.getKey())
        .as("re-activation must reuse the same job key")
        .isEqualTo(activation1.getKey());
    assertThat(activation2.getLeaseToken())
        .as("re-activation must advance the lease token")
        .isNotEqualTo(activation1.getLeaseToken());

    final long winningItemKey =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activation2,
            AgentInstanceHistoryRole.ASSISTANT,
            "Message from winning activation",
            OffsetDateTime.parse("2025-06-01T10:01:00Z"),
            2);

    awaitHistoryStatuses(
        agentInstanceKey,
        "both items PENDING before completion",
        tuple(supersededItemKey, AgentInstanceHistoryCommitStatus.PENDING),
        tuple(winningItemKey, AgentInstanceHistoryCommitStatus.PENDING));

    // Complete the winning activation — JobCompleteProcessor propagates the stored lease token
    // into AGENT_HISTORY:COMMIT, so visitByJobLease commits the winning item and discards the
    // superseded one.
    camundaClient.newCompleteCommand(activation2).execute();

    awaitHistoryStatuses(
        agentInstanceKey,
        "winning item COMMITTED, superseded item DISCARDED",
        tuple(winningItemKey, AgentInstanceHistoryCommitStatus.COMMITTED),
        tuple(supersededItemKey, AgentInstanceHistoryCommitStatus.DISCARDED));
  }

  @Test
  void shouldDedupeResendOfSameHistoryItemIdWithinSameJobActivation() {
    // --- setup: an activated agentic job on a fresh agent instance ---
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final long agentInstanceKey = createAgentInstance(elementInstanceKey);
    final var activatedJob = activateAgenticJob(processInstanceKey, true);

    final String historyItemId = UUID.randomUUID().toString();

    // when — the same historyItemId is submitted twice against the SAME job activation (same
    // jobKey/lease), e.g. an HTTP client retry. Content/metrics differ on purpose: dedup keys on
    // historyItemId alone, not on content — only the FIRST submission should stick.
    camundaClient
        .newUpdateAgentInstanceCommand(agentInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .jobKey(activatedJob.getKey())
        .jobLease(activatedJob.getLeaseToken())
        .history(
            List.of(
                new AgentInstanceHistoryItem()
                    .historyItemId(historyItemId)
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.ASSISTANT)
                    .content(
                        List.of(AgentInstanceHistoryContent.text("I can help with many tasks.")))
                    .producedAt(OffsetDateTime.parse("2025-06-01T10:00:00Z"))
                    .metrics(
                        new AgentInstanceHistoryMetrics()
                            .inputTokens(100L)
                            .outputTokens(50L)
                            .durationMs(500L))))
        .send()
        .join();

    final var secondResponse =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(activatedJob.getKey())
            .jobLease(activatedJob.getLeaseToken())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(historyItemId)
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.ASSISTANT)
                        .content(
                            List.of(AgentInstanceHistoryContent.text("A different resent answer.")))
                        .producedAt(OffsetDateTime.parse("2025-06-01T10:00:00Z"))
                        .metrics(
                            new AgentInstanceHistoryMetrics()
                                .inputTokens(999L)
                                .outputTokens(999L)
                                .durationMs(999L))))
            .send()
            .join();

    assertThat(secondResponse.getCreatedHistory())
        .as("the resent item must be flagged as a duplicate, not a new history entry")
        .singleElement()
        .satisfies(item -> assertThat(item.isDuplicate()).isTrue());

    camundaClient.newCompleteCommand(activatedJob).execute();

    // then
    Awaitility.await("resent history item deduped, not double-applied")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  camundaClient
                      .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                      .filter(f -> f.role(AgentInstanceHistoryRole.ASSISTANT))
                      .execute();
              assertThat(response.items())
                  .as("exactly one history item persists for the resent id")
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getContent())
                            .as("persisted content must match the FIRST submission, not the resend")
                            .extracting(c -> ((TextContent) c).getText())
                            .containsExactly("I can help with many tasks.");
                        assertThat(item.getMetrics())
                            .as("persisted metrics must match the FIRST submission, not the resend")
                            .isNotNull();
                        assertThat(item.getMetrics().getInputTokens()).isEqualTo(100L);
                        assertThat(item.getMetrics().getOutputTokens()).isEqualTo(50L);
                        assertThat(item.getMetrics().getDurationMs()).isEqualTo(500L);
                      });
            });
  }

  @Test
  void shouldApplyConfigurationChangeToAgentInstanceOnlyAfterJobCompletion() {
    // --- setup: a fresh agent instance, plus an activated job to carry the UPDATE below ---
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final long agentInstanceKey = createAgentInstance(elementInstanceKey);
    final var activatedJob = activateAgenticJob(processInstanceKey, true);

    // captured, not hardcoded — see createAgentInstance() for why.
    final var baseline =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.agentInstanceKey(agentInstanceKey))
            .execute()
            .items()
            .getFirst();
    final String baselineModel = baseline.getDefinition().getModel();
    final String baselineProvider = baseline.getDefinition().getProvider();
    final long baselineMaxTokens = baseline.getLimits().getMaxTokens();
    final int baselineMaxModelCalls = baseline.getLimits().getMaxModelCalls();
    final int baselineMaxToolCalls = baseline.getLimits().getMaxToolCalls();
    final int baselineToolCount = baseline.getTools().size();

    // when — WITHOUT completing the job, an UPDATE carries a CONFIGURATION item changing
    // model/provider/tools/limits.
    final long configHistoryItemKey =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(activatedJob.getKey())
            .jobLease(activatedJob.getLeaseToken())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(List.of(AgentInstanceHistoryContent.text("configuration change")))
                        .producedAt(OffsetDateTime.now())
                        .model("gpt-4o-mini")
                        .provider("azure-openai")
                        .tools(List.of(AgentTool.of("calculator")))
                        .limits(AgentInstanceLimits.of(5000L, 8, 16))))
            .send()
            .join()
            .getCreatedHistory()
            .getFirst()
            .getHistoryItemKey();

    // confirm the UPDATE actually landed as PENDING before checking the baseline still holds.
    // Without this, the baseline check below could pass purely because it ran before the write
    // was even indexed, not because the CONFIGURATION change is genuinely deferred until commit.
    Awaitility.await("CONFIGURATION history item indexed as PENDING")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var pendingItems =
                  camundaClient
                      .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                      .filter(
                          f ->
                              f.historyItemKey(configHistoryItemKey)
                                  .commitStatus(AgentInstanceHistoryCommitStatus.PENDING))
                      .execute()
                      .items();
              assertThat(pendingItems)
                  .as("CONFIGURATION history item must be indexed as PENDING before commit")
                  .hasSize(1);
            });

    // the write is confirmed indexed, so a direct assertion (no untilAsserted) is enough to prove
    // the AgentInstance itself still shows the baseline values.
    final var beforeCommit =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.agentInstanceKey(agentInstanceKey))
            .execute()
            .items()
            .getFirst();
    assertThat(beforeCommit.getDefinition().getModel())
        .as("model must still be the baseline; the CONFIGURATION change hasn't committed yet")
        .isEqualTo(baselineModel);
    assertThat(beforeCommit.getDefinition().getProvider())
        .as("provider must still be the baseline; the CONFIGURATION change hasn't committed yet")
        .isEqualTo(baselineProvider);
    assertThat(beforeCommit.getTools())
        .as("tools must still be the baseline; the CONFIGURATION change hasn't committed yet")
        .hasSize(baselineToolCount);
    assertThat(beforeCommit.getLimits().getMaxTokens())
        .as("maxTokens must still be the baseline; the CONFIGURATION change hasn't committed yet")
        .isEqualTo(baselineMaxTokens);
    assertThat(beforeCommit.getLimits().getMaxModelCalls())
        .as(
            "maxModelCalls must still be the baseline; the CONFIGURATION change hasn't committed"
                + " yet")
        .isEqualTo(baselineMaxModelCalls);
    assertThat(beforeCommit.getLimits().getMaxToolCalls())
        .as(
            "maxToolCalls must still be the baseline; the CONFIGURATION change hasn't committed"
                + " yet")
        .isEqualTo(baselineMaxToolCalls);

    camundaClient.newCompleteCommand(activatedJob).execute();

    Awaitility.await("agent instance definition reflects committed CONFIGURATION change")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var afterCommit =
                  camundaClient
                      .newAgentInstanceSearchRequest()
                      .filter(f -> f.agentInstanceKey(agentInstanceKey))
                      .execute()
                      .items()
                      .getFirst();
              assertThat(afterCommit.getDefinition().getModel())
                  .as("model must reflect the committed CONFIGURATION change")
                  .isEqualTo("gpt-4o-mini");
              assertThat(afterCommit.getDefinition().getProvider())
                  .as("provider must reflect the committed CONFIGURATION change")
                  .isEqualTo("azure-openai");
              assertThat(afterCommit.getTools())
                  .as("tools must reflect the committed CONFIGURATION change")
                  .extracting(AgentInstance.Tool::getName)
                  .containsExactly("calculator");
              assertThat(afterCommit.getLimits().getMaxTokens())
                  .as("maxTokens must reflect the committed CONFIGURATION change")
                  .isEqualTo(5000L);
              assertThat(afterCommit.getLimits().getMaxModelCalls())
                  .as("maxModelCalls must reflect the committed CONFIGURATION change")
                  .isEqualTo(8);
              assertThat(afterCommit.getLimits().getMaxToolCalls())
                  .as("maxToolCalls must reflect the committed CONFIGURATION change")
                  .isEqualTo(16);
            });
  }

  // --- helpers: each step below is called directly from the test bodies above ---

  private long deployAndStartProcessInstance() {
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                SERVICE_TASK_ID,
                t ->
                    t.zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
                        .zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    final var process =
        deployProcessAndWaitForIt(
            camundaClient, processModel, "agent-history-commit-lifecycle.bpmn");

    final var pi = startProcessInstance(camundaClient, process.getBpmnProcessId());
    final long processInstanceKey = pi.getProcessInstanceKey();

    waitForElementInstances(
        camundaClient, f -> f.elementId(SERVICE_TASK_ID).processInstanceKey(processInstanceKey), 1);

    return processInstanceKey;
  }

  private long getServiceTaskElementInstanceKey(final long processInstanceKey) {
    return camundaClient
        .newElementInstanceSearchRequest()
        .filter(f -> f.elementId(SERVICE_TASK_ID).processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .getFirst()
        .getElementInstanceKey();
  }

  private long createAgentInstance(final long elementInstanceKey) {
    // CREATE now requires a jobKey backed by an actual activation of the agentic job; fail it
    // straight back (no backoff) so the test's own activateAgenticJob call can still pick it up.
    final var activatedJob =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    final long agentInstanceKey =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .jobKey(activatedJob.getKey())
            .jobLease(activatedJob.getLeaseToken())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId(UUID.randomUUID().toString())
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(List.of(AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(OffsetDateTime.now())
                        .model("gpt-4o")
                        .provider("openai")
                        .systemPrompt(
                            List.of(
                                AgentInstanceHistoryContent.text("You are a helpful assistant.")))))
            .send()
            .join()
            .getAgentInstanceKey();

    // This also emits AGENT_HISTORY:DISCARD for the CONFIGURATION item just created above (no
    // committed snapshot exists yet, so model/provider/systemPrompt roll back to defaults). Tests
    // using this helper only assert on the non-CONFIGURATION history items they create afterward.
    camundaClient.newFailCommand(activatedJob).retries(1).execute();

    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);
    return agentInstanceKey;
  }

  private ActivatedJob activateAgenticJob(final long processInstanceKey, final boolean withLease) {
    final var activatedJobs =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .maxJobsToActivate(1)
            .withLease(withLease)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();
    assertThat(activatedJobs)
        .as("expected to activate one agent job for process instance %d", processInstanceKey)
        .isNotEmpty();
    return activatedJobs.getFirst();
  }

  private long createHistoryItem(
      final long agentInstanceKey,
      final long elementInstanceKey,
      final ActivatedJob activatedJob,
      final AgentInstanceHistoryRole role,
      final String text,
      final OffsetDateTime producedAt,
      final int loopIteration) {
    return camundaClient
        .newUpdateAgentInstanceCommand(agentInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .jobKey(activatedJob.getKey())
        .jobLease(activatedJob.getLeaseToken())
        .history(
            List.of(
                new AgentInstanceHistoryItem()
                    .historyItemId(UUID.randomUUID().toString())
                    .loopIteration(loopIteration)
                    .role(role)
                    .content(List.of(AgentInstanceHistoryContent.text(text)))
                    .producedAt(producedAt)))
        .send()
        .join()
        .getCreatedHistory()
        .getFirst()
        .getHistoryItemKey();
  }

  /**
   * Awaits until the agent instance's history items match exactly the expected (key, commit-status)
   * tuples. Filters by all commit statuses so that any spurious item of any status is visible (the
   * search API returns COMMITTED items by default) and so that leftover PENDING items would fail
   * the assertion rather than be silently hidden. Excludes the CONFIGURATION item that CREATE now
   * mandatorily establishes, since it is not one of the items under test in any of these scenarios.
   */
  private void awaitHistoryStatuses(
      final long agentInstanceKey, final String description, final Tuple... expected) {
    Awaitility.await(description)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                      .filter(
                          f ->
                              f.commitStatus(
                                      s ->
                                          s.in(
                                              AgentInstanceHistoryCommitStatus.PENDING,
                                              AgentInstanceHistoryCommitStatus.COMMITTED,
                                              AgentInstanceHistoryCommitStatus.DISCARDED))
                                  .role(r -> r.neq(AgentInstanceHistoryRole.CONFIGURATION)))
                      .execute()
                      .items();
              assertThat(items)
                  .extracting(
                      AgentInstanceHistory::getHistoryItemKey,
                      AgentInstanceHistory::getCommitStatus)
                  .containsExactlyInAnyOrder(expected);
            });
  }
}
