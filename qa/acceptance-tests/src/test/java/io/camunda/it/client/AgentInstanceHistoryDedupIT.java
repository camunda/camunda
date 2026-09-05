/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForAgentInstanceToBeIndexed;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Covers idempotency of the embedded {@code history[]} batch on {@code UpdateAgentInstance} across
 * job activations: an item committed by an earlier job, then resent by a later, different job for
 * the same agent instance, must still be recognized as a duplicate — over the real REST API, not
 * just at the engine level.
 */
@MultiDbTest
@CompatibilityTest
public class AgentInstanceHistoryDedupIT {

  private static final String AGENT_ELEMENT_ID = "agentDedupElement";
  private static final String PROCESS_ID = "agentHistoryDedupProcess";
  private static final String JOB_TYPE = "agent-task";

  private static CamundaClient camundaClient;

  @Test
  void shouldFlagResentHistoryItemAsDuplicateAcrossJobActivations() {
    // given — a sequential multi-instance AI-agent service task: job1 pushes "item-x" and
    // completes, then job2 — a brand-new job on a brand-new element instance — resends "item-x"
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                AGENT_ELEMENT_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeAiAgentTaskDefinition()
                        .multiInstance(
                            m ->
                                m.sequential()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();
    final var process =
        deployProcessAndWaitForIt(camundaClient, processModel, "agent-history-dedup.bpmn");

    final var processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .processDefinitionKey(process.getProcessDefinitionKey())
            .variables(Map.of("items", List.of("a", "b")))
            .execute()
            .getProcessInstanceKey();

    waitForElementInstances(
        camundaClient,
        f ->
            f.elementId(AGENT_ELEMENT_ID)
                .type(ElementInstanceType.SERVICE_TASK)
                .processInstanceKey(processInstanceKey),
        1);
    final var ei1 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(
                f ->
                    f.elementId(AGENT_ELEMENT_ID)
                        .type(ElementInstanceType.SERVICE_TASK)
                        .processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    // CREATE requires a jobKey/jobLease backed by an actual activation of the agentic job; fail
    // it straight back so the activation below can pick the same job up again
    final var creationJob =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();
    final var agentInstanceKey =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ei1)
            .jobKey(creationJob.getKey())
            .jobLease(creationJob.getLeaseToken())
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
    camundaClient.newFailCommand(creationJob).retries(1).execute();
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);

    final var job1 =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    // when — job1 pushes "item-x", then completes, committing it
    final var firstResponse =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(ei1)
            .jobKey(job1.getKey())
            .jobLease(job1.getLeaseToken())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId("item-x")
                        .role(AgentInstanceHistoryRole.USER)
                        .loopIteration(1)
                        .content(List.of(AgentInstanceHistoryContent.text("hi")))
                        .producedAt(OffsetDateTime.now())))
            .send()
            .join();
    final var originalKey = firstResponse.getCreatedHistory().getFirst().getHistoryItemKey();

    camundaClient.newCompleteCommand(job1).send().join();

    // given — the multi-instance body advances: a second element instance activates
    waitForElementInstances(
        camundaClient,
        f ->
            f.elementId(AGENT_ELEMENT_ID)
                .type(ElementInstanceType.SERVICE_TASK)
                .processInstanceKey(processInstanceKey),
        2);
    final var ei2 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(
                f ->
                    f.elementId(AGENT_ELEMENT_ID)
                        .type(ElementInstanceType.SERVICE_TASK)
                        .processInstanceKey(processInstanceKey))
            .sort(s -> s.elementInstanceKey().asc())
            .execute()
            .items()
            .get(1)
            .getElementInstanceKey();

    final var job2 =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    // when — job2 (a brand-new job, on a brand-new element instance) resends "item-x", with
    // different content
    final var secondResponse =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(ei2)
            .jobKey(job2.getKey())
            .jobLease(job2.getLeaseToken())
            .history(
                List.of(
                    new AgentInstanceHistoryItem()
                        .historyItemId("item-x")
                        .role(AgentInstanceHistoryRole.USER)
                        .loopIteration(1)
                        .content(List.of(AgentInstanceHistoryContent.text("hi again")))
                        .producedAt(OffsetDateTime.now())))
            .send()
            .join();

    // then
    final var echoed = secondResponse.getCreatedHistory();
    assertThat(echoed).hasSize(1);
    assertThat(echoed.getFirst().isDuplicate())
        .as("resent item is flagged a duplicate of the original, over the real REST API")
        .isTrue();
    assertThat(echoed.getFirst().getHistoryItemKey()).isEqualTo(originalKey);
    assertThat(job2.getElementInstanceKey())
        .as("job2 must be the job activated for the second element instance")
        .isEqualTo(ei2);
  }
}
