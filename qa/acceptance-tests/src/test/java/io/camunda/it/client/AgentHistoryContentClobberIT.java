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
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.TextContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Reproduces the {@code AgentHistoryHandler} field-clobber bug end-to-end, through a real broker
 * and a real {@code CamundaClient}: {@code updateEntity()} unconditionally sets {@code content},
 * {@code toolCalls}, {@code metrics} and {@code producedAt} on every call, while {@code
 * ExporterBatchWriter} caches one mutable entity per id for a whole export batch. The engine strips
 * those fields from the COMMITTED/DISCARDED event value by design (see {@code
 * AgentHistoryCreatedApplier}), so if a CREATED event and its terminal counterpart for the same
 * history item land in one export batch, the terminal event's empty/default values clobber the
 * CREATED event's real values before the document is ever persisted.
 *
 * <p>This IT forces that co-residence by raising the exporter's {@code bulk.delay} well beyond how
 * long a real job completion takes, so the CREATED event (from item creation) and the COMMIT event
 * (from job completion, issued immediately after) are still both sitting unflushed in the same
 * in-memory batch when the second is applied. Co-residence is proven, not assumed: a search for the
 * item performed right after creation but before completion must find nothing, because if the
 * CREATED event had already been flushed on its own, the two events could not possibly coalesce
 * into one batch and this test would not exercise the bug.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AgentHistoryContentClobberIT {

  private static final String SERVICE_TASK_ID = "agentTask";
  private static final String PROCESS_ID = "agentHistoryContentClobberProcess";
  private static final int BULK_DELAY_SECONDS = 30;

  @MultiDbTestApplication(managedLifecycle = false)
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withUnauthenticatedAccess();

  @BeforeAll
  static void setUp() {
    final var camundaExporter = CamundaExporter.class.getSimpleName().toLowerCase();
    STANDALONE_CAMUNDA.withUnifiedConfig(
        c -> {
          final var newArgs =
              new HashMap<>(c.getData().getExporters().get(camundaExporter).getArgs());
          // Size high enough (production default) that the small number of records in this test
          // never triggers a size-based flush, and memoryLimit high enough that the tiny records
          // here never trigger a memory-based flush either. delay is generous enough that a job
          // completion issued immediately after item creation reliably lands in the same batch as
          // the CREATED event, well before any scheduled flush fires.
          newArgs.put(
              "bulk", Map.of("size", 5000, "delay", BULK_DELAY_SECONDS, "memoryLimit", 100));
          c.getData().getExporters().get(camundaExporter).setArgs(newArgs);
        });

    STANDALONE_CAMUNDA.start();
    STANDALONE_CAMUNDA.awaitCompleteTopology();
  }

  @AfterAll
  static void tearDown() {
    STANDALONE_CAMUNDA.stop();
  }

  @Test
  void shouldNotLoseContentToolCallsMetricsAndProducedAtOnJobCompletion() {
    try (final var camundaClient = STANDALONE_CAMUNDA.newClientBuilder().build()) {
      // given - an activated agentic job with an agent instance ready to receive history items
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
              camundaClient, processModel, "agent-history-content-clobber.bpmn");
      final var processInstanceKey =
          startProcessInstance(camundaClient, process.getBpmnProcessId()).getProcessInstanceKey();

      waitForElementInstances(
          camundaClient,
          f -> f.elementId(SERVICE_TASK_ID).processInstanceKey(processInstanceKey),
          1);
      final var elementInstanceKey =
          camundaClient
              .newElementInstanceSearchRequest()
              .filter(f -> f.elementId(SERVICE_TASK_ID).processInstanceKey(processInstanceKey))
              .execute()
              .items()
              .getFirst()
              .getElementInstanceKey();

      final var agentInstanceKey =
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
      final var jobKey = activatedJobs.get(0).getKey();

      // when - create ONE ASSISTANT history item with real content, tool calls and metrics. Since
      // ASSISTANT is the LLM-response role, metrics are only meaningful on this role.
      final var producedAt = OffsetDateTime.parse("2025-06-01T10:00:00Z");
      final var historyItemKey =
          camundaClient
              .newCreateAgentHistoryItemCommand(agentInstanceKey)
              .elementInstanceKey(elementInstanceKey)
              .jobKey(jobKey)
              .role(AgentInstanceHistoryRole.ASSISTANT)
              .content(List.of(AgentInstanceHistoryContent.text("the original assistant response")))
              .producedAt(producedAt)
              .toolCalls(
                  List.of(
                      new AgentInstanceHistoryToolCall()
                          .toolCallId("tc-1")
                          .toolName("search")
                          .elementId("searchElement")
                          .arguments(Map.of("query", "weather"))))
              .metrics(
                  new AgentInstanceHistoryMetrics()
                      .inputTokens(50)
                      .outputTokens(30)
                      .durationMs(1200))
              .send()
              .join()
              .getHistoryItemKey();

      // self-proof of co-residence: with bulk.delay generous and nothing forcing an earlier
      // flush, the CREATED event for this item must still be sitting unflushed in the exporter's
      // in-memory batch right now. If it were already visible here, the CREATED event would have
      // been flushed on its own before job completion, the CREATED and COMMIT events could not
      // possibly coalesce into one batch, and this test would not exercise the bug at all.
      final var beforeCompletion =
          camundaClient
              .newAgentInstanceHistorySearchRequest(agentInstanceKey)
              .filter(f -> f.historyItemKey(historyItemKey))
              .execute()
              .items();
      assertThat(beforeCompletion)
          .as(
              "the history item must not be visible yet - otherwise the CREATED event already"
                  + " flushed on its own and this test would not prove co-residence with the"
                  + " COMMIT event")
          .isEmpty();

      // complete the job immediately - simulating the LLM responding - so the engine emits
      // AGENT_HISTORY:COMMIT for this item right away, well within the bulk.delay window, so it
      // coalesces into the same in-memory batch as the still-unflushed CREATED event above.
      camundaClient.newCompleteCommand(jobKey).execute();

      // then - once the item is genuinely indexed and committed (not merely "not yet visible"),
      // it must still hold the original CREATED values. Under the bug, updateEntity() already
      // overwrote the shared cached entity with the terminal event's empty/default values before
      // the document was ever written, so the document is created empty.
      final var committedItem = new AtomicReference<AgentInstanceHistory>();
      Awaitility.await("the history item is indexed and transitions to COMMITTED")
          .atMost(Duration.ofSeconds(BULK_DELAY_SECONDS).plus(TIMEOUT_DATA_AVAILABILITY))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var items =
                    camundaClient
                        .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                        .filter(f -> f.historyItemKey(historyItemKey))
                        .execute()
                        .items();
                assertThat(items).as("exactly one history item must be indexed").hasSize(1);
                assertThat(items.getFirst().getCommitStatus())
                    .as("the history item must have transitioned to COMMITTED")
                    .isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
                committedItem.set(items.getFirst());
              });

      final var item = committedItem.get();
      SoftAssertions.assertSoftly(
          softly -> {
            softly
                .assertThat(item.getContent())
                .as("content must still hold the original CREATED text, not be empty")
                .extracting(c -> ((TextContent) c).getText())
                .containsExactly("the original assistant response");
            softly
                .assertThat(item.getToolCalls())
                .as("toolCalls must still hold the original CREATED tool call, not be empty")
                .extracting(
                    AgentInstanceHistoryToolCall::getToolCallId,
                    AgentInstanceHistoryToolCall::getToolName,
                    AgentInstanceHistoryToolCall::getElementId,
                    AgentInstanceHistoryToolCall::getArguments)
                .containsExactly(
                    tuple("tc-1", "search", "searchElement", Map.of("query", "weather")));
            softly.assertThat(item.getMetrics()).as("metrics must not be null").isNotNull();
            if (item.getMetrics() != null) {
              softly
                  .assertThat(item.getMetrics().getInputTokens())
                  .as("inputTokens must still hold the original CREATED value, not zero")
                  .isEqualTo(50L);
              softly
                  .assertThat(item.getMetrics().getOutputTokens())
                  .as("outputTokens must still hold the original CREATED value, not zero")
                  .isEqualTo(30L);
              softly
                  .assertThat(item.getMetrics().getDurationMs())
                  .as("durationMs must still hold the original CREATED value, not zero")
                  .isEqualTo(1200L);
            }
            // producedAt must equal the exact original value, not merely be non-null: a clobbered
            // producedAt silently falls back to the COMMIT event's own timestamp rather than
            // becoming null, so a "non-empty" check alone would not catch this failure mode.
            softly
                .assertThat(item.getProducedAt())
                .as(
                    "producedAt must equal the exact original CREATED value, not the COMMIT"
                        + " event's fallback timestamp")
                .isEqualTo(producedAt);
          });
    }
  }
}
