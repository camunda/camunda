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
    // --- setup: deploy, start, get element instance ---
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

    final long elementInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(SERVICE_TASK_ID).processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    // --- create agent instance ---
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

    // --- activate the agentic job ---
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
    final long jobKey = activatedJobs.get(0).getKey();

    // --- create two history items ---
    final long historyItemKey1 =
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

    final long historyItemKey2 =
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

    // --- verify items are PENDING before job completion ---
    // Filter by all statuses so that any spurious item of any commit status is visible;
    // an all-status filter is needed because the search API returns COMMITTED items by default.
    Awaitility.await("history items indexed as PENDING")
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
                  .containsExactlyInAnyOrder(
                      tuple(historyItemKey1, AgentInstanceHistoryCommitStatus.PENDING),
                      tuple(historyItemKey2, AgentInstanceHistoryCommitStatus.PENDING));
            });

    // --- complete the job → engine emits COMMIT → items become COMMITTED ---
    camundaClient.newCompleteCommand(jobKey).execute();

    // --- verify items are COMMITTED after job completion ---
    // Filter by all statuses to confirm no items remain in PENDING or DISCARDED state.
    Awaitility.await("history items transitioned to COMMITTED")
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
                  .containsExactlyInAnyOrder(
                      tuple(historyItemKey1, AgentInstanceHistoryCommitStatus.COMMITTED),
                      tuple(historyItemKey2, AgentInstanceHistoryCommitStatus.COMMITTED));
            });
  }
}
