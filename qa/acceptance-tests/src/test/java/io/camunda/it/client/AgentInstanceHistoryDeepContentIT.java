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
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.ObjectContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Regression test for deeply nested {@code AgentHistoryRecord} content.
 *
 * <p>Pins nesting depth {@value #NESTING_DEPTH} as a full round-trip guard (client -&gt; broker
 * -&gt; engine -&gt; exporter -&gt; secondary-storage read), safely below the read-failure boundary
 * found while investigating <a href="https://github.com/camunda/camunda/issues/57230">issue
 * #57230</a> — see <a href="https://github.com/camunda/camunda/pull/62166">PR #62166</a> for that
 * exploration, which established that Elasticsearch/OpenSearch stop being able to read content back
 * once it nests past depth 993.
 */
@MultiDbTest
@CompatibilityTest
public class AgentInstanceHistoryDeepContentIT {

  private static final int NESTING_DEPTH = 990;
  private static final String SERVICE_TASK_ID = "agentTask";
  private static final String PROCESS_ID = "agentHistoryDeepContentProcess";
  private static final String AGENT_JOB_TYPE = "agent-task";

  private static CamundaClient camundaClient;

  @Test
  void shouldRoundTripDeeplyNestedObjectContentThroughFullStack() {
    // given
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final var job = activateAgenticJob();
    final long agentInstanceKey = createAgentInstance(elementInstanceKey, job);
    final var nestedObject = nestedMap(NESTING_DEPTH);

    // when
    camundaClient
        .newUpdateAgentInstanceCommand(agentInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .jobKey(job.getKey())
        .jobLease(job.getLeaseToken())
        .history(
            List.of(
                new AgentInstanceHistoryItem()
                    .historyItemId(UUID.randomUUID().toString())
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.ASSISTANT)
                    .content(List.of(AgentInstanceHistoryContent.object(nestedObject)))
                    .producedAt(OffsetDateTime.now())))
        .execute();
    camundaClient.newCompleteCommand(job).execute();

    // then
    Awaitility.await("deeply nested object content is searchable via secondary storage")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                      .filter(f -> f.role(AgentInstanceHistoryRole.ASSISTANT))
                      .execute()
                      .items();
              assertThat(items)
                  .as(
                      "exactly one ASSISTANT history item should be indexed in secondary storage for agent instance %d",
                      agentInstanceKey)
                  .singleElement()
                  .satisfies(
                      item ->
                          assertThat(item.getContent())
                              .as("ASSISTANT item must carry exactly one OBJECT content block")
                              .singleElement()
                              .isInstanceOf(ObjectContent.class)
                              .extracting(c -> ((ObjectContent) c).getObject())
                              .as(
                                  "object content nested %d levels deep must round-trip exactly"
                                      + " through client -> broker -> engine -> exporter -> secondary storage",
                                  NESTING_DEPTH)
                              .isEqualTo(nestedObject));
            });
  }

  @Test
  void shouldRoundTripDeeplyNestedToolCallArgumentsThroughFullStack() {
    // given
    final long processInstanceKey = deployAndStartProcessInstance();
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final var job = activateAgenticJob();
    final long agentInstanceKey = createAgentInstance(elementInstanceKey, job);
    final var nestedArguments = nestedMap(NESTING_DEPTH);

    // when
    camundaClient
        .newUpdateAgentInstanceCommand(agentInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .jobKey(job.getKey())
        .jobLease(job.getLeaseToken())
        .history(
            List.of(
                new AgentInstanceHistoryItem()
                    .historyItemId(UUID.randomUUID().toString())
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.ASSISTANT)
                    .content(List.of(AgentInstanceHistoryContent.text("calling a tool")))
                    .producedAt(OffsetDateTime.now())
                    .toolCalls(
                        List.of(
                            new AgentInstanceHistoryToolCall()
                                .toolCallId(UUID.randomUUID().toString())
                                .toolName("test-tool")
                                .elementId(SERVICE_TASK_ID)
                                .arguments(nestedArguments)))))
        .execute();
    camundaClient.newCompleteCommand(job).execute();

    // then
    Awaitility.await("deeply nested tool call arguments are searchable via secondary storage")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                      .filter(f -> f.role(AgentInstanceHistoryRole.ASSISTANT))
                      .execute()
                      .items();
              assertThat(items)
                  .as(
                      "exactly one ASSISTANT history item should be indexed in secondary storage for agent instance %d",
                      agentInstanceKey)
                  .singleElement()
                  .satisfies(
                      item ->
                          assertThat(item.getToolCalls())
                              .as("ASSISTANT item must carry exactly one tool call")
                              .singleElement()
                              .extracting(AgentInstanceHistoryToolCall::getArguments)
                              .as(
                                  "tool call arguments nested %d levels deep must round-trip"
                                      + " exactly through client -> broker -> engine -> exporter -> secondary storage",
                                  NESTING_DEPTH)
                              .isEqualTo(nestedArguments));
            });
  }

  private long deployAndStartProcessInstance() {
    final var processModel =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                SERVICE_TASK_ID, t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    final var process =
        deployProcessAndWaitForIt(camundaClient, processModel, "agent-history-deep-content.bpmn");

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

  private long createAgentInstance(final long elementInstanceKey, final ActivatedJob job) {
    return camundaClient
        .newCreateAgentInstanceCommand()
        .elementInstanceKey(elementInstanceKey)
        .jobKey(job.getKey())
        .jobLease(job.getLeaseToken())
        .history(
            List.of(
                new AgentInstanceHistoryItem()
                    .historyItemId(UUID.randomUUID().toString())
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.CONFIGURATION)
                    .content(List.of(AgentInstanceHistoryContent.text("configuration")))
                    .producedAt(OffsetDateTime.now())
                    .model("test-model")
                    .provider("test-provider")
                    .systemPrompt(
                        List.of(AgentInstanceHistoryContent.text("You are a helpful assistant.")))))
        .execute()
        .getAgentInstanceKey();
  }

  private ActivatedJob activateAgenticJob() {
    final var activatedJobs =
        camundaClient
            .newActivateJobsCommand()
            .jobType(AGENT_JOB_TYPE)
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .execute()
            .getJobs();
    assertThat(activatedJobs).as("expected to activate one agent job").isNotEmpty();
    return activatedJobs.getFirst();
  }

  /**
   * Builds a chain of {@code depth} single-key nested maps: {@code {"nested": {"nested": ...}}}.
   */
  private static Map<String, Object> nestedMap(final int depth) {
    Map<String, Object> current = new HashMap<>();
    current.put("leaf", true);
    for (int i = 0; i < depth - 1; i++) {
      final Map<String, Object> next = new HashMap<>();
      next.put("nested", current);
      current = next;
    }
    return current;
  }
}
