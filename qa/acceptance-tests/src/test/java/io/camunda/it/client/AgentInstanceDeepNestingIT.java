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
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.ProblemException;
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
import java.util.function.Function;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exploratory test for https://github.com/camunda/camunda/issues/57230.
 *
 * <p>Unlike {@code AgentInstanceDeepNestingTest} (zeebe/qa/integration-tests, which only checks the
 * command layer against a bare broker), this test exercises the realistic path: an ASSISTANT-role
 * history item with deeply nested {@code object} content or tool-call {@code arguments} is
 * submitted via {@code UPDATE} — the way real agent output actually reaches `AgentHistoryRecord` —
 * is committed by completing the job, and is then read back through the real {@code CamundaClient}
 * search API, backed by a real secondary storage (Elasticsearch / OpenSearch / RDBMS, whichever
 * {@code @MultiDbTest} is running against).
 *
 * <p>{@code AgentHistoryMessageContent#object} and {@code AgentHistoryEmbeddedToolCall#arguments}
 * round-trip through {@code MsgPackConverter}'s {@code MESSSAGE_PACK_OBJECT_MAPPER}, which disables
 * Jackson's read-side nesting depth guard entirely. The exporter re-serializes every record to JSON
 * for indexing — calling exactly those getters — so if a payload survives UPDATE but blows up
 * during export, the history item (or the whole agent instance) never becomes queryable, i.e. it is
 * silently lost to the API consumer with no rejection at write time.
 *
 * <p>This is investigation only, not a fix: each depth is reported (and any inaccessibility
 * flagged) rather than asserted into a fixed pass/fail, since the whole point is to find where the
 * current, unguarded behavior breaks down.
 *
 * <p><b>Observed finding — run against each locally-runnable {@code DatabaseType} via {@code
 * -Dtest.integration.camunda.database.type=<ES|OS|RDBMS_H2>}, identical for both content kinds:</b>
 *
 * <ul>
 *   <li><b>depth &le; 993</b> — the last depth that fully succeeds end-to-end on every backend:
 *       UPDATE accepted, committed, and confirmed searchable via secondary storage.
 *   <li><b>depth 994–995</b> — <b>Elasticsearch and OpenSearch only:</b> UPDATE <em>succeeds</em>
 *       (the item is written), but the subsequent search read fails with a {@code 500 Internal
 *       Server Error}. Both clients' search-response JSON mapper ({@code
 *       co.elastic.clients.json.jackson}/{@code org.opensearch.client.json.jackson}) deserializes
 *       the search hit via a separate Jackson {@code ObjectMapper} that keeps Jackson's
 *       <em>unrelaxed default</em> {@code StreamReadConstraints} (max depth 1000, exceeded at depth
 *       1001 through the {@code AgentHistoryEntity["content"]->...->["object"]} reference chain —
 *       identical exception on both). The item is written but becomes permanently unreadable via
 *       the search API — a real, reproduced instance of the same write-succeeds-read-fails
 *       asymmetry that made #54335 dangerous for {@code VariableRecord}, now confirmed for {@code
 *       AgentHistoryRecord} via real secondary storage.
 *       <p><b>RDBMS (H2) does not reproduce this</b> — depths 994 and 995 round-trip successfully.
 *       {@code AgentHistoryDbModel}'s reader ({@code
 *       db/rdbms/.../write/domain/AgentHistoryDbModel.java}) deserializes just the raw JSON {@code
 *       content} column via a plain, unguarded {@code ObjectMapper}, without the extra
 *       response-envelope nesting ({@code hits.hits[]._source...}) that the ES/OS client libraries
 *       add on top of the stored document when parsing the whole search hit. That extra envelope
 *       depth is exactly what pushes ES/OS over the 1000-depth ceiling two levels earlier than the
 *       write-side gate below.
 *   <li><b>depth 996</b> — rejected at the gateway itself, before it ever reaches the engine, on
 *       every backend: Spring's Jackson HTTP message converter hits its own default {@code
 *       StreamReadConstraints} (max depth 1000) parsing the incoming request body, surfacing as a
 *       clean {@code 400 Bad Request}.
 *   <li><b>depth &ge; 997</b> — rejected by the Java client itself, before any network call, on
 *       every backend: the client's own {@code ObjectMapper} hits Jackson's default {@code
 *       StreamWriteConstraints} (max depth 1000) while serializing the request body.
 * </ul>
 *
 * <p>Note the asymmetry with {@code AgentInstanceDeepNestingTest} (zeebe/qa/integration-tests):
 * that test's {@code Record#toJson()} exporter simulation does <em>not</em> reproduce the
 * depth-994/995 ES/OS failure above, because it never round-trips through a real search client's
 * deserialization — the real bug only surfaces with an actual secondary-storage read, which only
 * this test exercises.
 */
@MultiDbTest
@CompatibilityTest
public class AgentInstanceDeepNestingIT {

  private static final String SERVICE_TASK_ID = "agentTask";

  private static CamundaClient camundaClient;

  /**
   * Nesting depths probed by both parameterized tests below, from safely shallow to well past the
   * client-side rejection boundary, with fine-grained steps between 900 and 999 to pinpoint the
   * exact depth at which the Java client starts rejecting the request.
   */
  private static IntStream nestingDepths() {
    return IntStream.of(10, 500, 900, 950, 990, 993, 994, 995, 996, 997, 998, 999, 1_000, 1_500);
  }

  @ParameterizedTest(name = "objectContentNestingDepth={0}")
  @MethodSource("nestingDepths")
  void shouldFlagIfDeeplyNestedObjectContentIsUnavailableViaSecondaryStorage(final int depth) {
    exploreNestingDepth(depth, "nested-object-" + depth, this::assistantItemWithObjectContent);
  }

  @ParameterizedTest(name = "toolCallArgumentsNestingDepth={0}")
  @MethodSource("nestingDepths")
  void shouldFlagIfDeeplyNestedToolCallArgumentsAreUnavailableViaSecondaryStorage(final int depth) {
    exploreNestingDepth(depth, "nested-args-" + depth, this::assistantItemWithToolCallArguments);
  }

  private void exploreNestingDepth(
      final int depth,
      final String suffix,
      final Function<Map<String, Object>, AgentInstanceHistoryItem> itemBuilder) {
    // given — a fresh process/agent instance, with a real job activation backing the UPDATE, the
    // way the AI-agent feature itself submits history: after CREATE, via UPDATE on job completion.
    final long processInstanceKey = deployAndStartProcessInstance(suffix);
    final long elementInstanceKey = getServiceTaskElementInstanceKey(processInstanceKey);
    final long agentInstanceKey = createAgentInstance(elementInstanceKey, suffix);
    final ActivatedJob job = activateAgenticJob(suffix);
    final AgentInstanceHistoryItem deepItem = itemBuilder.apply(nestedMap(depth));

    // when
    final Throwable updateFailure =
        attemptUpdate(agentInstanceKey, elementInstanceKey, job, deepItem);
    if (updateFailure != null) {
      System.out.println(
          "[57230]["
              + suffix
              + "] depth="
              + depth
              + " -> UPDATE FAILED at CLIENT/REST layer "
              + classify(updateFailure)
              + ": "
              + rootCauseMessage(updateFailure));
      return;
    }

    // committing the PENDING item is what makes it visible via the default search filter
    camundaClient.newCompleteCommand(job).send().join();

    // then — must become visible via secondary storage within a bounded time; flag if not
    try {
      Awaitility.await(
              "agent instance history item for depth " + depth + " indexed in secondary storage")
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(
              () -> {
                final var items =
                    camundaClient
                        .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                        .filter(f -> f.role(AgentInstanceHistoryRole.ASSISTANT))
                        .execute()
                        .items();
                assertThat(items).as("history item must be searchable").isNotEmpty();
              });
      System.out.println(
          "[57230]["
              + suffix
              + "] depth="
              + depth
              + " -> SUCCEEDED: accessible via secondary storage (history search)");
    } catch (final Throwable t) {
      System.out.println(
          "[57230]["
              + suffix
              + "] depth="
              + depth
              + " -> FLAGGED: NOT accessible via secondary storage after UPDATE succeeded ("
              + t.getClass().getName()
              + "): "
              + rootCauseMessage(t));
    }
  }

  private Throwable attemptUpdate(
      final long agentInstanceKey,
      final long elementInstanceKey,
      final ActivatedJob job,
      final AgentInstanceHistoryItem item) {
    try {
      camundaClient
          .newUpdateAgentInstanceCommand(agentInstanceKey)
          .elementInstanceKey(elementInstanceKey)
          .jobKey(job.getKey())
          .jobLease(job.getLeaseToken())
          .history(List.of(item))
          .send()
          .join();
      return null;
    } catch (final Throwable t) {
      return t;
    }
  }

  private String classify(final Throwable failure) {
    if (failure instanceof final ProblemException problemException) {
      return "REST ("
          + problemException.details().getStatus()
          + " "
          + problemException.details().getTitle()
          + ")";
    }
    return "CLIENT/CONNECTION (" + failure.getClass().getName() + ")";
  }

  private static String rootCauseMessage(final Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage();
  }

  private long deployAndStartProcessInstance(final String suffix) {
    final var processModel =
        Bpmn.createExecutableProcess("nesting-process-" + suffix)
            .startEvent()
            .serviceTask(
                SERVICE_TASK_ID, t -> t.zeebeJobType(jobType(suffix)).zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();
    final var process =
        deployProcessAndWaitForIt(camundaClient, processModel, "agent-nesting-" + suffix + ".bpmn");
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

  private long createAgentInstance(final long elementInstanceKey, final String suffix) {
    // CREATE requires a jobKey backed by an actual activation of the agentic job; fail it straight
    // back (no backoff) so activateAgenticJob() below can pick it up again for the real UPDATE.
    final var activatedJob = activateAgenticJob(suffix);
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
                        .model("test-model")
                        .provider("test-provider")
                        .systemPrompt(
                            List.of(
                                AgentInstanceHistoryContent.text("You are a helpful assistant.")))))
            .send()
            .join()
            .getAgentInstanceKey();
    camundaClient.newFailCommand(activatedJob).retries(1).execute();
    waitForAgentInstanceToBeIndexed(camundaClient, agentInstanceKey);
    return agentInstanceKey;
  }

  private ActivatedJob activateAgenticJob(final String suffix) {
    final var activatedJobs =
        camundaClient
            .newActivateJobsCommand()
            .jobType(jobType(suffix))
            .maxJobsToActivate(1)
            .withLease(true)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs();
    assertThat(activatedJobs).as("expected to activate one agent job for %s", suffix).isNotEmpty();
    return activatedJobs.getFirst();
  }

  private static String jobType(final String suffix) {
    return "agent-task-" + suffix;
  }

  private AgentInstanceHistoryItem assistantItemWithObjectContent(
      final Map<String, Object> nested) {
    return new AgentInstanceHistoryItem()
        .historyItemId(UUID.randomUUID().toString())
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.ASSISTANT)
        .content(List.of(AgentInstanceHistoryContent.object(nested)))
        .producedAt(OffsetDateTime.now());
  }

  private AgentInstanceHistoryItem assistantItemWithToolCallArguments(
      final Map<String, Object> nestedArguments) {
    return new AgentInstanceHistoryItem()
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
                    .arguments(nestedArguments)));
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
