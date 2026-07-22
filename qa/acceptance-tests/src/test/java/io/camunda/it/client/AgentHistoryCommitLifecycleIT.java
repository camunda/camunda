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
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
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
    final var activatedJob = activateAgenticJob(processInstanceKey, false);
    final long historyItemKey1 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.USER,
            "Hello, what can you do?",
            OffsetDateTime.parse("2025-06-01T10:00:00Z"));
    final long historyItemKey2 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.ASSISTANT,
            "I can help with many tasks.",
            OffsetDateTime.parse("2025-06-01T10:01:00Z"));
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
    final var activatedJob = activateAgenticJob(processInstanceKey, false);
    final long historyItemKey1 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.USER,
            "Hello, what can you do?",
            OffsetDateTime.parse("2025-06-01T10:00:00Z"));
    final long historyItemKey2 =
        createHistoryItem(
            agentInstanceKey,
            elementInstanceKey,
            activatedJob,
            AgentInstanceHistoryRole.ASSISTANT,
            "I can help with many tasks.",
            OffsetDateTime.parse("2025-06-01T10:01:00Z"));
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
            OffsetDateTime.parse("2025-06-01T10:00:00Z"));
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
            OffsetDateTime.parse("2025-06-01T10:01:00Z"));
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

  // --- helpers: each step below is called directly from the test bodies above ---

  private long deployAndStartProcessInstance() {
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                SERVICE_TASK_ID,
                t -> t.zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX))
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
    final long agentInstanceKey =
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
      final OffsetDateTime producedAt) {
    final var finalCommandStep =
        camundaClient
            .newCreateAgentHistoryItemCommand(agentInstanceKey)
            .elementInstanceKey(elementInstanceKey)
            .jobKey(activatedJob.getKey())
            .role(role)
            .content(List.of(AgentInstanceHistoryContent.text(text)))
            .producedAt(producedAt);
    if (activatedJob.getLeaseToken() != null) {
      finalCommandStep.jobLease(activatedJob.getLeaseToken());
    }
    return finalCommandStep.execute().getHistoryItemKey();
  }

  /**
   * Awaits until the agent instance's history items match exactly the expected (key, commit-status)
   * tuples. Filters by all commit statuses so that any spurious item of any status is visible (the
   * search API returns COMMITTED items by default) and so that leftover PENDING items would fail
   * the assertion rather than be silently hidden.
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
                                          AgentInstanceHistoryCommitStatus.DISCARDED)))
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
