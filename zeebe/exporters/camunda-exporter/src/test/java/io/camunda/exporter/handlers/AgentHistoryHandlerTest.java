/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.COMMIT_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentType;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryEmbeddedToolCallValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryLimitsValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryToolValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryRole;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
import io.camunda.webapps.schema.entities.document.DocumentReferenceMetadataEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceLimitsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDocumentReferenceMetadataValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDocumentReferenceValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class AgentHistoryHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = AgentHistoryTemplate.INDEX_NAME;
  private final AgentHistoryHandler underTest = new AgentHistoryHandler(indexName);

  @Test
  void shouldReturnCorrectHandlerMetadata() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.AGENT_HISTORY);
    assertThat(underTest.getEntityType()).isEqualTo(AgentHistoryEntity.class);
    assertThat(underTest.getIndexName()).isEqualTo(indexName);
  }

  @ParameterizedTest(name = "[{index}] Should handle ''{0}'' record")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final AgentHistoryIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isTrue();
  }

  @ParameterizedTest(name = "[{index}] Should not handle ''{0}'' record")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final AgentHistoryIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isFalse();
  }

  @Test
  void shouldExtractIdAndIndexes() {
    // given
    final TargetIndexLocator indexLocator = mock(TargetIndexLocator.class);
    final TargetIndex index = TargetIndex.mainIndex(indexName);
    when(indexLocator.locateOrdinalIndex(eq(indexName), any())).thenReturn(index);
    final Record<AgentHistoryRecordValue> record = factory.generateRecord(ValueType.AGENT_HISTORY);

    // when - then
    assertThat(underTest.extractIdAndIndexes(indexLocator, record))
        .containsExactly(new IdAndIndex(String.valueOf(record.getKey()), index));
  }

  @Test
  void shouldPopulateAllFieldsForCreatedIntent() {
    // given — updateEntity() must populate ALL fields for the CREATED intent.
    final long recordKey = 100L;
    final int partitionId = 1;
    final long agentInstanceKey = 50L;
    final long elementInstanceKey = 200L;
    final long processInstanceKey = 300L;
    final long rootProcessInstanceKey = 250L;
    final long processDefinitionKey = 400L;
    final String tenantId = "<default>";
    final long jobKey = 500L;
    final String jobLease = "lease-token-abc";
    final int loopIteration = 3;
    final long producedAtMs = System.currentTimeMillis();
    final long inputTokens = 50L;
    final long outputTokens = 30L;
    final long reasoningTokenCount = 15L;
    final long cacheCreationTokenCount = 9L;
    final long cacheReadTokenCount = 5L;
    final long durationMs = 1200L;

    final var textContent =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
            .withText("Hello, world!")
            .withObject(Map.of())
            .build();

    final var toolCall =
        ImmutableAgentHistoryEmbeddedToolCallValue.builder()
            .withToolCallId("tc-1")
            .withToolName("search")
            .withElementId("searchElement")
            .withArguments(Map.of("query", "weather"))
            .build();

    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .withBpmnProcessId("my-process")
            .withProcessDefinitionKey(processDefinitionKey)
            .withTenantId(tenantId)
            .withJobKey(jobKey)
            .withJobLease(jobLease)
            .withLoopIteration(loopIteration)
            .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.ASSISTANT)
            .withProducedAt(producedAtMs)
            .withMetrics(
                ImmutableAgentHistoryMetricsValue.builder()
                    .withInputTokens(inputTokens)
                    .withOutputTokens(outputTokens)
                    .withReasoningTokenCount(reasoningTokenCount)
                    .withCacheCreationTokenCount(cacheCreationTokenCount)
                    .withCacheReadTokenCount(cacheReadTokenCount)
                    .withDurationMs(durationMs)
                    .build())
            .withContent(List.of(textContent))
            .withToolCalls(List.of(toolCall))
            .build();

    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValue(recordValue));

    final var entity = new AgentHistoryEntity().setId(String.valueOf(recordKey));

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(entity.getElementInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(entity.getBpmnProcessId()).isEqualTo("my-process");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getJobKey()).isEqualTo(jobKey);
    assertThat(entity.getJobLease()).isEqualTo(jobLease);
    assertThat(entity.getLoopIteration()).isEqualTo(loopIteration);
    assertThat(entity.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
    assertThat(entity.getCommitStatus()).isEqualTo(AgentHistoryCommitStatus.PENDING);
    assertThat(entity.getProducedAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(producedAtMs)));
    assertThat(entity.getInputTokens()).isEqualTo(inputTokens);
    assertThat(entity.getOutputTokens()).isEqualTo(outputTokens);
    assertThat(entity.getReasoningTokenCount()).isEqualTo(reasoningTokenCount);
    assertThat(entity.getCacheCreationTokenCount()).isEqualTo(cacheCreationTokenCount);
    assertThat(entity.getCacheReadTokenCount()).isEqualTo(cacheReadTokenCount);
    assertThat(entity.getDurationMs()).isEqualTo(durationMs);
    assertThat(entity.getContent())
        .containsExactly(
            new AgentHistoryContentValue(
                AgentHistoryContentType.TEXT, "Hello, world!", null, null));
    assertThat(entity.getToolCalls())
        .containsExactly(
            new AgentHistoryEmbeddedToolCallValue(
                "tc-1", "search", "searchElement", Map.of("query", "weather")));
  }

  @Test
  void shouldPopulateConfigurationFieldsForCreatedIntent() {
    // given — a CONFIGURATION item that touched
    // historyItemId/tools/model/provider/limits/systemPrompt
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(50L, 3))
            .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.CONFIGURATION)
            .withHistoryItemId("history-item-1")
            .withTools(
                List.of(
                    ImmutableAgentInstanceToolValue.builder()
                        .withName("search")
                        .withDescription("Searches the web")
                        .withElementId("Task_1")
                        .build()))
            .withModel("gpt-4o")
            .withProvider("openai")
            .withLimits(
                ImmutableAgentInstanceLimitsValue.builder()
                    .withMaxTokens(1000L)
                    .withMaxModelCalls(10)
                    .withMaxToolCalls(5)
                    .build())
            .withSystemPrompt(
                List.of(
                    ImmutableAgentHistoryMessageContentValue.builder()
                        .withContentType(
                            io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
                        .withText("You are a helpful assistant.")
                        .withObject(Map.of())
                        .build()))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(101L).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("101");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getRole()).isEqualTo(AgentHistoryRole.CONFIGURATION);
    assertThat(entity.getHistoryItemId()).isEqualTo("history-item-1");
    assertThat(entity.getTools())
        .containsExactly(new AgentHistoryToolValue("search", "Searches the web", "Task_1"));
    assertThat(entity.getModel()).isEqualTo("gpt-4o");
    assertThat(entity.getProvider()).isEqualTo("openai");
    assertThat(entity.getLimits()).isEqualTo(new AgentHistoryLimitsValue(1000L, 10, 5));
    assertThat(entity.getSystemPrompt())
        .containsExactly(
            new AgentHistoryContentValue(
                AgentHistoryContentType.TEXT, "You are a helpful assistant.", null, null));
  }

  @Test
  void shouldMapUntouchedConfigurationFields() {
    // given — a non-CONFIGURATION item (protocol defaults: empty historyItemId/tools/model/
    // provider/systemPrompt, all-sentinel limits)
    final var recordValue = buildMinimalRecordValue(50L, 3);
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(102L).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("102");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getHistoryItemId()).isNull();
    assertThat(entity.getTools()).isEmpty();
    assertThat(entity.getModel()).isNull();
    assertThat(entity.getProvider()).isNull();
    assertThat(entity.getLimits()).isEqualTo(new AgentHistoryLimitsValue(-1L, -1, -1));
    assertThat(entity.getSystemPrompt()).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] Terminal ''{0}'' event must not clobber CREATED content")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"COMMITTED", "DISCARDED"})
  void shouldPreserveContentToolCallsMetricsAndProducedAtWhenTerminalEventFollowsCreated(
      final AgentHistoryIntent intent) {
    // given — a CREATED record with distinct, non-default content, toolCalls, metrics and
    // producedAt
    final long recordKey = 100L;
    final int partitionId = 1;
    final long originalProducedAtMs = 1_000_000_000_000L;
    // explicit (not ProtocolFactory's random default) so the clobber's fallback timestamp is
    // deterministic
    final long terminalRecordTimestampMs = 1_600_000_000_000L;

    final var createdValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(50L, 3))
            .withProducedAt(originalProducedAtMs)
            .withMetrics(
                ImmutableAgentHistoryMetricsValue.builder()
                    .withInputTokens(50L)
                    .withOutputTokens(30L)
                    .withReasoningTokenCount(15L)
                    .withCacheCreationTokenCount(9L)
                    .withCacheReadTokenCount(5L)
                    .withDurationMs(1200L)
                    .build())
            .withContent(
                List.of(
                    ImmutableAgentHistoryMessageContentValue.builder()
                        .withContentType(
                            io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
                        .withText("the original user prompt")
                        .withObject(Map.of())
                        .build()))
            .withToolCalls(
                List.of(
                    ImmutableAgentHistoryEmbeddedToolCallValue.builder()
                        .withToolCallId("tc-1")
                        .withToolName("search")
                        .withElementId("searchElement")
                        .withArguments(Map.of("query", "weather"))
                        .build()))
            .withHistoryItemId("history-item-1")
            .withTools(
                List.of(
                    ImmutableAgentInstanceToolValue.builder()
                        .withName("search")
                        .withDescription("Searches the web")
                        .withElementId("Task_1")
                        .build()))
            .withModel("gpt-4o")
            .withProvider("openai")
            .withLimits(
                ImmutableAgentInstanceLimitsValue.builder()
                    .withMaxTokens(1000L)
                    .withMaxModelCalls(10)
                    .withMaxToolCalls(5)
                    .build())
            .withSystemPrompt(
                List.of(
                    ImmutableAgentHistoryMessageContentValue.builder()
                        .withContentType(
                            io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.TEXT)
                        .withText("You are a helpful assistant.")
                        .withObject(Map.of())
                        .build()))
            .build();

    final Record<AgentHistoryRecordValue> createdRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValue(createdValue));

    final var entity = new AgentHistoryEntity().setId(String.valueOf(recordKey));

    underTest.updateEntity(createdRecord, entity);

    // when — a trimmed terminal event (empty content/toolCalls, zero metrics, producedAt 0L) lands
    // on the SAME shared entity in the same batch, as the engine emits for COMMITTED/DISCARDED
    final Record<AgentHistoryRecordValue> trimmedRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withTimestamp(terminalRecordTimestampMs)
                    .withValue(
                        ImmutableAgentHistoryRecordValue.builder()
                            .from(createdValue)
                            .withProducedAt(0L)
                            .withMetrics(
                                ImmutableAgentHistoryMetricsValue.builder()
                                    .withInputTokens(0L)
                                    .withOutputTokens(0L)
                                    .withReasoningTokenCount(0L)
                                    .withCacheCreationTokenCount(0L)
                                    .withCacheReadTokenCount(0L)
                                    .withDurationMs(0L)
                                    .build())
                            .withContent(List.of())
                            .withToolCalls(List.of())
                            .withHistoryItemId("")
                            .withTools(List.of())
                            .withModel("")
                            .withProvider("")
                            .withLimits(
                                ImmutableAgentInstanceLimitsValue.builder()
                                    .withMaxTokens(-1L)
                                    .withMaxModelCalls(-1)
                                    .withMaxToolCalls(-1)
                                    .build())
                            .withSystemPrompt(List.of())
                            .build()));

    underTest.updateEntity(trimmedRecord, entity);

    // then — the original CREATED values for content, toolCalls, metrics and producedAt must
    // survive; only commitStatus should reflect the terminal event.
    final AgentHistoryCommitStatus expectedStatus =
        switch (intent) {
          case COMMITTED -> AgentHistoryCommitStatus.COMMITTED;
          case DISCARDED -> AgentHistoryCommitStatus.DISCARDED;
          default -> throw new IllegalStateException("Unexpected intent: " + intent);
        };

    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(entity.getContent())
              .as("content must still hold the original CREATED text")
              .containsExactly(
                  new AgentHistoryContentValue(
                      AgentHistoryContentType.TEXT, "the original user prompt", null, null));
          softly
              .assertThat(entity.getToolCalls())
              .as("toolCalls must still hold the original CREATED tool call")
              .containsExactly(
                  new AgentHistoryEmbeddedToolCallValue(
                      "tc-1", "search", "searchElement", Map.of("query", "weather")));
          softly
              .assertThat(entity.getInputTokens())
              .as("inputTokens must still hold the original CREATED value")
              .isEqualTo(50L);
          softly
              .assertThat(entity.getOutputTokens())
              .as("outputTokens must still hold the original CREATED value")
              .isEqualTo(30L);
          softly
              .assertThat(entity.getReasoningTokenCount())
              .as("reasoningTokenCount must still hold the original CREATED value")
              .isEqualTo(15L);
          softly
              .assertThat(entity.getCacheCreationTokenCount())
              .as("cacheCreationTokenCount must still hold the original CREATED value")
              .isEqualTo(9L);
          softly
              .assertThat(entity.getCacheReadTokenCount())
              .as("cacheReadTokenCount must still hold the original CREATED value")
              .isEqualTo(5L);
          softly
              .assertThat(entity.getDurationMs())
              .as("durationMs must still hold the original CREATED value")
              .isEqualTo(1200L);
          softly
              .assertThat(entity.getProducedAt())
              .as(
                  "producedAt must still hold the original CREATED value, not the fallback"
                      + " timestamp of the terminal event")
              .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(originalProducedAtMs)));
          softly
              .assertThat(entity.getCommitStatus())
              .as("commitStatus must reflect the terminal event that was actually applied")
              .isEqualTo(expectedStatus);
          softly
              .assertThat(entity.getHistoryItemId())
              .as("historyItemId must still hold the original CREATED value")
              .isEqualTo("history-item-1");
          softly
              .assertThat(entity.getTools())
              .as("tools must still hold the original CREATED tool list")
              .containsExactly(new AgentHistoryToolValue("search", "Searches the web", "Task_1"));
          softly
              .assertThat(entity.getModel())
              .as("model must still hold the original CREATED value")
              .isEqualTo("gpt-4o");
          softly
              .assertThat(entity.getProvider())
              .as("provider must still hold the original CREATED value")
              .isEqualTo("openai");
          softly
              .assertThat(entity.getLimits())
              .as("limits must still hold the original CREATED value")
              .isEqualTo(new AgentHistoryLimitsValue(1000L, 10, 5));
          softly
              .assertThat(entity.getSystemPrompt())
              .as("systemPrompt must still hold the original CREATED value")
              .containsExactly(
                  new AgentHistoryContentValue(
                      AgentHistoryContentType.TEXT, "You are a helpful assistant.", null, null));
        });
  }

  @Test
  void shouldFlushWithUpsertContainingOnlyCommitStatus() {
    // given — entity populated via updateEntity
    final var recordValue = buildMinimalRecordValue(1L, 1);
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(1L).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");
    underTest.updateEntity(record, entity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then — only commitStatus is included in the upsert updateFields map
    verify(mockRequest)
        .upsert(index, entity.getId(), entity, Map.of(COMMIT_STATUS, entity.getCommitStatus()));
  }

  @ParameterizedTest(name = "[{index}] Should map protocol role ''{0}'' to entity role")
  @EnumSource(
      value = io.camunda.zeebe.protocol.record.value.AgentHistoryRole.class,
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllRoleValues(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryRole protocolRole) {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withRole(protocolRole)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — each protocol role maps to an entity role with the same name
    assertThat(entity.getRole()).isNotNull();
    assertThat(entity.getRole().name())
        .as(
            """
            Protocol role '%s' has no explicit mapping in 'AgentHistoryHandler.mapRole()' \
            and falls back to 'UNKNOWN' — add '%s' to '%s' entity enum and handle \
            it explicitly in the switch, or exclude it from this test if UNKNOWN is intentional.\
            """,
            protocolRole.name(), protocolRole.name(), AgentHistoryRole.class.getSimpleName())
        .isEqualTo(protocolRole.name());
  }

  @Test
  void shouldThrowOnUnspecifiedRole() {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.UNSPECIFIED)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when / then
    assertThatThrownBy(() -> underTest.updateEntity(record, entity))
        .as(
            "If a new AgentHistoryRole is added, add an explicit case to AgentHistoryHandler.mapRole()")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected UNSPECIFIED AgentHistoryRole on an exported record");
  }

  @ParameterizedTest(name = "[{index}] Intent ''{0}'' should map to the expected commitStatus")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATE", "COMMIT", "DISCARD"},
      mode = Mode.EXCLUDE)
  void shouldMapIntentToExpectedCommitStatus(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(intent).withValue(buildMinimalRecordValue(1L, 1)));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — commitStatus is derived from intent, not from the record value
    final AgentHistoryCommitStatus expected =
        switch (intent) {
          case CREATED -> AgentHistoryCommitStatus.PENDING;
          default -> AgentHistoryCommitStatus.valueOf(intent.name());
        };
    assertThat(entity.getCommitStatus()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] Unexpected ''{0}'' should throw")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"},
      mode = Mode.EXCLUDE)
  void shouldThrowForUnexpectedIntent(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(intent).withValue(buildMinimalRecordValue(1L, 1)));
    final var entity = new AgentHistoryEntity().setId("1");

    // when / then
    assertThatThrownBy(() -> underTest.updateEntity(record, entity))
        .as(
            "If a new AgentHistoryIntent is handled, add an explicit case to AgentHistoryHandler.mapCommitStatusFromIntent()")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected AgentHistoryIntent on an exported record: " + intent);
  }

  @Test
  void shouldMapObjectContentType() {
    // given
    final Map<String, Object> objectData = Map.of("result", "42", "confidence", "high");
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.OBJECT)
            .withText("")
            .withObject(objectData)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — object field is populated; text and documentReference are null
    assertThat(entity.getContent())
        .singleElement()
        .satisfies(
            content -> {
              assertThat(content.contentType()).isEqualTo(AgentHistoryContentType.OBJECT);
              assertThat(content.text()).isNull();
              assertThat(content.documentReference()).isNull();
              assertThat(content.object()).isEqualTo(objectData);
            });
  }

  @Test
  void shouldMapNonMapObjectContent() {
    // given — OBJECT content with a non-map value (array of scalars)
    final var arrayValue = List.of(10, 20, 30);
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.OBJECT)
            .withText("")
            .withObject(arrayValue)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getContent())
        .singleElement()
        .satisfies(
            content -> {
              assertThat(content.contentType()).isEqualTo(AgentHistoryContentType.OBJECT);
              assertThat(content.text()).isNull();
              assertThat(content.documentReference()).isNull();
              assertThat(content.object()).isEqualTo(arrayValue);
            });
  }

  @Test
  void shouldThrowOnUnspecifiedContentType() {
    // given — UNSPECIFIED is the protocol sentinel; it cannot occur for a real exported record
    final var unspecifiedItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(
                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.UNSPECIFIED)
            .withText("some text")
            .withObject(Map.of("key", "value"))
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(unspecifiedItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when / then
    assertThatThrownBy(() -> underTest.updateEntity(record, entity))
        .as(
            "If a new AgentHistoryContentType is added, add an explicit case to AgentHistoryHandler.mapContent()")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected UNSPECIFIED AgentHistoryContentType on an exported record");
  }

  @ParameterizedTest(
      name = "[{index}] Protocol type ''{0}'' should have a matching entity enum constant")
  @EnumSource(
      value = io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.class,
      names = "UNSPECIFIED",
      mode = Mode.EXCLUDE)
  void shouldMapAllSupportedContentTypes(
      final io.camunda.zeebe.protocol.record.value.AgentHistoryContentType protocolType) {
    // This test will fail if a new content type is added to the protocol without a corresponding
    // constant in the entity AgentHistoryContentType enum. Add the new constant to fix it.
    assertThatCode(() -> AgentHistoryContentType.valueOf(protocolType.name()))
        .as(
            "AgentHistoryContentType must have a constant named '%s' to match protocol type %s."
                + " Add the constant to AgentHistoryContentType and handle it properly in AgentHistoryHandler.",
            protocolType.name(), protocolType)
        .doesNotThrowAnyException();
  }

  @Test
  void shouldConvertNegativeExpiresAtToNull() {
    // given — document reference with expiresAt == -1 (sentinel for "no expiry")
    final var metadata =
        ImmutableDocumentReferenceMetadataValue.builder()
            .withExpiresAt(-1L)
            .withSize(1024L)
            .withProcessInstanceKey(-1L)
            .build();
    final var docRef = ImmutableDocumentReferenceValue.builder().withMetadata(metadata).build();
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(
                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.DOCUMENT)
            .withText("")
            .withObject(Map.of())
            .withDocumentReference(docRef)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    final DocumentReferenceEntity docRefEntity = entity.getContent().getFirst().documentReference();
    assertThat(docRefEntity).isNotNull();
    final DocumentReferenceMetadataEntity metaEntity = docRefEntity.metadata();
    assertThat(metaEntity.expiresAt()).isNull();
    assertThat(metaEntity.processInstanceKey()).isNull();
  }

  @Test
  void shouldMapDocumentContentWithValidMetadata() {
    // given — document reference with positive expiresAt and processInstanceKey (not sentinels)
    final long expiresAtMs = System.currentTimeMillis() + 86_400_000L;
    final long docProcessInstanceKey = 42L;
    final var metadata =
        ImmutableDocumentReferenceMetadataValue.builder()
            .withExpiresAt(expiresAtMs)
            .withSize(2048L)
            .withProcessInstanceKey(docProcessInstanceKey)
            .withContentType("application/pdf")
            .withFileName("report.pdf")
            .withProcessDefinitionId("my-process")
            .withCustomProperties(Map.of("source", "upload"))
            .build();
    final var docRef =
        ImmutableDocumentReferenceValue.builder()
            .withDocumentId("doc-1")
            .withStoreId("store-1")
            .withContentHash("abc123")
            .withMetadata(metadata)
            .build();
    final var contentItem =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(
                io.camunda.zeebe.protocol.record.value.AgentHistoryContentType.DOCUMENT)
            .withText("")
            .withObject(Map.of())
            .withDocumentReference(docRef)
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withContent(List.of(contentItem))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then — valid expiresAt and processInstanceKey are preserved (not nulled)
    final DocumentReferenceEntity docRefEntity = entity.getContent().getFirst().documentReference();
    assertThat(docRefEntity).isNotNull();
    assertThat(docRefEntity.documentId()).isEqualTo("doc-1");
    assertThat(docRefEntity.storeId()).isEqualTo("store-1");
    assertThat(docRefEntity.contentHash()).isEqualTo("abc123");
    final DocumentReferenceMetadataEntity metaEntity = docRefEntity.metadata();
    assertThat(metaEntity.expiresAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(expiresAtMs)));
    assertThat(metaEntity.size()).isEqualTo(2048L);
    assertThat(metaEntity.processInstanceKey()).isEqualTo(docProcessInstanceKey);
    assertThat(metaEntity.contentType()).isEqualTo("application/pdf");
    assertThat(metaEntity.fileName()).isEqualTo("report.pdf");
    assertThat(metaEntity.processDefinitionId()).isEqualTo("my-process");
    assertThat(metaEntity.customProperties()).isEqualTo(Map.of("source", "upload"));
  }

  @Test
  void shouldStoreProvidedLoopIterationAsIs() {
    // given — a typical value plus zero/negative ones. The handler mirrors whatever the record
    // carries: it is not its place to "correct" an out-of-contract value, only the
    // engine/connector side is expected to guarantee a positive loopIteration in practice.
    final var typicalRecordValue =
        ImmutableAgentHistoryRecordValue.builder().from(buildMinimalRecordValue(1L, 5)).build();
    final var zeroRecordValue =
        ImmutableAgentHistoryRecordValue.builder().from(buildMinimalRecordValue(1L, 0)).build();
    final var negativeRecordValue =
        ImmutableAgentHistoryRecordValue.builder().from(buildMinimalRecordValue(1L, -1)).build();
    final Record<AgentHistoryRecordValue> typicalRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(typicalRecordValue));
    final Record<AgentHistoryRecordValue> zeroRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(zeroRecordValue));
    final Record<AgentHistoryRecordValue> negativeRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(negativeRecordValue));
    final var typicalEntity = new AgentHistoryEntity().setId("1");
    final var zeroEntity = new AgentHistoryEntity().setId("2");
    final var negativeEntity = new AgentHistoryEntity().setId("3");

    // when
    underTest.updateEntity(typicalRecord, typicalEntity);
    underTest.updateEntity(zeroRecord, zeroEntity);
    underTest.updateEntity(negativeRecord, negativeEntity);

    // then — all values are stored as provided, unchanged
    assertThat(typicalEntity.getLoopIteration()).isEqualTo(5);
    assertThat(zeroEntity.getLoopIteration()).isEqualTo(0);
    assertThat(negativeEntity.getLoopIteration()).isEqualTo(-1);
  }

  @Test
  void shouldFallBackToRecordTimestampWhenProducedAtNonPositive() {
    // given — producedAt == -1 is the protocol default for "unset"; fall back to the record
    // timestamp so the non-nullable API contract is preserved
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withProducedAt(-1L)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getProducedAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
  }

  @Test
  void shouldMapUnsetMetricsToNull() {
    // given — -1L is the protocol sentinel for "not provided", for all six metrics fields
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildMinimalRecordValue(1L, 1))
            .withMetrics(
                ImmutableAgentHistoryMetricsValue.builder()
                    .withInputTokens(-1L)
                    .withOutputTokens(-1L)
                    .withReasoningTokenCount(-1L)
                    .withCacheCreationTokenCount(-1L)
                    .withCacheReadTokenCount(-1L)
                    .withDurationMs(-1L)
                    .build())
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withValue(recordValue));
    final var entity = new AgentHistoryEntity().setId("1");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getInputTokens()).isNull();
    assertThat(entity.getOutputTokens()).isNull();
    assertThat(entity.getReasoningTokenCount()).isNull();
    assertThat(entity.getCacheCreationTokenCount()).isNull();
    assertThat(entity.getCacheReadTokenCount()).isNull();
    assertThat(entity.getDurationMs()).isNull();
  }

  // --- helpers ---

  private Record<AgentHistoryRecordValue> generateRecord(final AgentHistoryIntent intent) {
    return factory.generateRecord(ValueType.AGENT_HISTORY, r -> r.withIntent(intent));
  }

  private AgentHistoryRecordValue buildMinimalRecordValue(
      final long agentInstanceKey, final int loopIteration) {
    return ImmutableAgentHistoryRecordValue.builder()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(1L)
        .withProcessInstanceKey(10L)
        .withRootProcessInstanceKey(10L)
        .withBpmnProcessId("my-process")
        .withProcessDefinitionKey(20L)
        .withTenantId("<default>")
        .withJobKey(30L)
        .withJobLease("lease")
        .withLoopIteration(loopIteration)
        .withRole(io.camunda.zeebe.protocol.record.value.AgentHistoryRole.ASSISTANT)
        .withProducedAt(System.currentTimeMillis())
        .withMetrics(
            ImmutableAgentHistoryMetricsValue.builder()
                .withInputTokens(0L)
                .withOutputTokens(0L)
                .withDurationMs(0L)
                .build())
        .withContent(List.of())
        .withToolCalls(List.of())
        .build();
  }
}
