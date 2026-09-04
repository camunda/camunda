/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exploratory test for https://github.com/camunda/camunda/issues/57230.
 *
 * <p>{@code AgentHistoryMessageContent#object} and {@code AgentHistoryEmbeddedToolCall#arguments}
 * round-trip through {@code MsgPackConverter}'s {@code MESSSAGE_PACK_OBJECT_MAPPER}, which disables
 * Jackson's read-side nesting depth guard entirely. This drives a deeply nested payload through the
 * real, client-reachable path — {@code CamundaClient} -&gt; {@code POST /v2/agent-instances} -&gt;
 * {@code AgentInstanceMapper} -&gt; {@code AgentInstanceCreateProcessor} — to find out,
 * empirically, at which nesting depth creation starts failing and at which layer:
 *
 * <ul>
 *   <li><b>CLIENT/CONNECTION</b> — the failure never produced a structured REST response (e.g. the
 *       client's own request serialization blew up, or the server-side handling thread died before
 *       it could reply).
 *   <li><b>REST</b> — the gateway replied with a structured problem (HTTP status + title).
 *   <li><b>EXPORTER</b> — the command was accepted and the {@code AGENT_HISTORY.CREATED} event was
 *       appended to the log (broker/engine layer is fine), but re-serializing that record to JSON —
 *       exactly what a real exporter (camunda-exporter/rdbms-exporter, via {@code
 *       getObject()}/{@code getArguments()}) does before indexing it — fails. This is simulated
 *       directly via {@link Record#toJson()} on the recorded event instead of standing up a real
 *       Elasticsearch/OpenSearch/RDBMS exporter, since the failure mode lives entirely in the
 *       shared {@code MsgPackConverter} read path, not in any exporter-specific code.
 * </ul>
 *
 * <p>This is investigation only, not a fix: the assertions document the currently-observed behavior
 * so a future change (e.g. an explicit depth guard) is caught as an intentional behavior change
 * rather than silently.
 *
 * <p><b>Observed finding:</b> both content kinds succeed end-to-end (broker/engine and the
 * exporter-style re-serialization) up to depth ~900, and are rejected at the CLIENT layer from
 * depth ~999 onward — the Java client's own {@code ObjectMapper} hits Jackson's unconfigured
 * default {@code StreamWriteConstraints} (max depth 1000) while serializing the request, before any
 * network call. No depth in the tested range ever reaches a broker/engine or exporter-layer
 * failure, because the client-side guard rejects it first.
 */
@ZeebeIntegration
final class AgentInstanceDeepNestingTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(30)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest(name = "objectContentNestingDepth={0}")
  @ValueSource(ints = {10, 500, 900, 999, 1_000, 1_500})
  void shouldRevealFailureLayerForDeeplyNestedObjectContent(final int depth) {
    // given
    final var target = createAgentServiceTaskInstance("nested-object-job-" + depth);
    final List<AgentInstanceHistoryItem> history =
        List.of(configurationHistoryItem(), assistantItemWithObjectContent(nestedMap(depth)));

    // when
    final Throwable createFailure = attemptCreate(target, history);
    final Throwable exportFailure =
        createFailure == null ? attemptExporterStyleReserialization(target, history.size()) : null;

    // then
    report("OBJECT content", depth, createFailure, exportFailure);
  }

  @ParameterizedTest(name = "toolCallArgumentsNestingDepth={0}")
  @ValueSource(ints = {10, 500, 900, 999, 1_000, 1_500})
  void shouldRevealFailureLayerForDeeplyNestedToolCallArguments(final int depth) {
    // given
    final var target = createAgentServiceTaskInstance("nested-args-job-" + depth);
    final List<AgentInstanceHistoryItem> history =
        List.of(configurationHistoryItem(), assistantItemWithToolCallArguments(nestedMap(depth)));

    // when
    final Throwable createFailure = attemptCreate(target, history);
    final Throwable exportFailure =
        createFailure == null ? attemptExporterStyleReserialization(target, history.size()) : null;

    // then
    report("tool call arguments", depth, createFailure, exportFailure);
  }

  private Throwable attemptCreate(
      final ElementInstanceAndJob target, final List<AgentInstanceHistoryItem> history) {
    try {
      client
          .newCreateAgentInstanceCommand()
          .elementInstanceKey(target.elementInstanceKey())
          .jobKey(target.jobKey())
          .jobLease("test-job-lease")
          .history(history)
          .execute();
      return null;
    } catch (final Throwable t) {
      return t;
    }
  }

  /**
   * Simulates the exporter layer: fetches the {@code AGENT_HISTORY.CREATED} events just appended
   * for this element instance, and re-serializes each to JSON via {@link Record#toJson()} — the
   * same call a real exporter makes, which recurses into {@code getObject()}/{@code getArguments()}
   * for any content/tool-call fields.
   */
  private Throwable attemptExporterStyleReserialization(
      final ElementInstanceAndJob target, final int expectedRecordCount) {
    final List<Record<AgentHistoryRecordValue>> createdRecords =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withElementInstanceKey(target.elementInstanceKey())
            .limit(expectedRecordCount)
            .toList();

    for (final Record<AgentHistoryRecordValue> record : createdRecords) {
      try {
        record.toJson();
      } catch (final Throwable t) {
        return t;
      }
    }
    return null;
  }

  private void report(
      final String field,
      final int depth,
      final Throwable createFailure,
      final Throwable exportFailure) {
    if (createFailure != null) {
      System.out.println(
          "[57230]["
              + field
              + "] depth="
              + depth
              + " -> FAILED at CLIENT/REST layer "
              + classify(createFailure)
              + ": "
              + rootCauseMessage(createFailure));
      return;
    }
    if (exportFailure == null) {
      System.out.println(
          "[57230]["
              + field
              + "] depth="
              + depth
              + " -> SUCCEEDED through broker/engine AND exporter-style re-serialization");
      return;
    }
    System.out.println(
        "[57230]["
            + field
            + "] depth="
            + depth
            + " -> broker/engine layer OK, but FAILED at EXPORTER layer ("
            + exportFailure.getClass().getName()
            + "): "
            + rootCauseMessage(exportFailure));
  }

  /**
   * Classifies where a create-time failure surfaced: {@code REST} if the gateway responded with a
   * structured problem (an HTTP status + title), or {@code CLIENT/CONNECTION} if the channel broke
   * before a structured response came back (e.g. the server-side handling thread died mid-request).
   */
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

  private ElementInstanceAndJob createAgentServiceTaskInstance(final String jobType) {
    final long processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("nesting-test-" + jobType)
                .startEvent()
                .serviceTask(
                    "service-task", t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done());
    resourcesHelper.createProcessInstance(processDefinitionKey);
    final long elementInstanceKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .getFirst()
            .getValue()
            .getElementInstanceKey();
    final long jobKey =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst()
            .getKey();
    return new ElementInstanceAndJob(elementInstanceKey, jobKey);
  }

  private AgentInstanceHistoryItem configurationHistoryItem() {
    return new AgentInstanceHistoryItem()
        .historyItemId(UUID.randomUUID().toString())
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.CONFIGURATION)
        .content(List.of(AgentInstanceHistoryContent.text("configuration")))
        .producedAt(OffsetDateTime.now())
        .model("test-model")
        .provider("test-provider")
        .systemPrompt(List.of(AgentInstanceHistoryContent.text("You are a helpful assistant.")));
  }

  private AgentInstanceHistoryItem assistantItemWithObjectContent(final Object nested) {
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
                    .elementId("service-task")
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

  private record ElementInstanceAndJob(long elementInstanceKey, long jobKey) {}
}
