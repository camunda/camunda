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
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers idempotency of the embedded {@code history[]} batch on {@code UpdateAgentInstance} across
 * job activations (window 3): an item committed by an earlier job, then resent by a later,
 * different job for the same agent instance, must still be recognized as a duplicate — over the
 * real REST API, not just at the engine level.
 *
 * <p>Kept as its own class rather than added to {@link AgentInstanceHistorySearchIT}: that class's
 * fixture is a single shared {@code @BeforeAll} agent instance reused by every {@code @Test} method
 * for search-filter assertions. This scenario needs its own multi-instance process and asserts on
 * the synchronous command response, not on search results, so sharing that fixture would only add
 * coupling.
 */
@MultiDbTest
@CompatibilityTest
public class AgentInstanceHistoryDedupIT {

  private static final String AGENT_ELEMENT_ID = "agentDedupElement";
  private static final String PROCESS_ID = "agentHistoryDedupProcess";
  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  private static CamundaClient camundaClient;

  @Test
  void shouldFlagResentHistoryItemAsDuplicateAcrossJobActivations() {
    // given — a sequential multi-instance AI-agent service task: job1 pushes "item-x" and
    // completes (which commits the item via AGENT_HISTORY:COMMIT), then job2 — a brand-new job on
    // a brand-new element instance, for the same agent instance — resends "item-x".
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

    final var agentInstanceKey =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ei1)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .send()
            .join()
            .getAgentInstanceKey();
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);

    final var job1 =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    // when — job1 pushes "item-x", then completes: AgentHistoryCommitProcessor commits it.
    final var firstResponse =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(ei1)
            .jobKey(job1.getKey())
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

    camundaClient.newCompleteCommand(job1.getKey()).send().join();

    // given — the multi-instance body advances: a second, brand-new element instance activates.
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
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    // when — job2 (a brand-new job, on a brand-new element instance) resends "item-x", with
    // different content (dedup keys on historyItemId alone, not content).
    final var secondResponse =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(ei2)
            .jobKey(job2.getKey())
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

    // then — the resent item is echoed back flagged as a duplicate of the original, carrying the
    // original's key, not a newly minted one — over the real REST API.
    final var echoed = secondResponse.getCreatedHistory();
    assertThat(echoed).hasSize(1);
    assertThat(echoed.getFirst().isDuplicate()).isTrue();
    assertThat(echoed.getFirst().getHistoryItemKey()).isEqualTo(originalKey);

    // supporting check — confirms job2 really was activated on the second element instance, as
    // the test setup intended.
    assertThat(job2.getElementInstanceKey())
        .as("job2 must be the job activated for the second element instance")
        .isEqualTo(ei2);
  }
}
