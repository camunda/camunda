/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.db.rdbms.write.service.AgentHistoryWriter;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryEmbeddedToolCallValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentHistoryExportHandlerTest {

  private static final Set<AgentHistoryIntent> EXPORTABLE_INTENTS =
      EnumSet.of(
          AgentHistoryIntent.CREATED, AgentHistoryIntent.COMMITTED, AgentHistoryIntent.DISCARDED);

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private AgentHistoryWriter writer;
  @Captor private ArgumentCaptor<AgentHistoryDbModel> modelCaptor;

  private AgentHistoryExportHandler handler;

  static Stream<AgentHistoryIntent> exportableIntents() {
    return EXPORTABLE_INTENTS.stream();
  }

  static Stream<AgentHistoryIntent> nonExportableIntents() {
    return Stream.of(AgentHistoryIntent.values())
        .filter(Predicate.not(EXPORTABLE_INTENTS::contains));
  }

  @BeforeEach
  void setUp() {
    handler = new AgentHistoryExportHandler(writer);
  }

  @ParameterizedTest(name = "Should export record with intent: {0}")
  @MethodSource("exportableIntents")
  void shouldExportRecord(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(ValueType.AGENT_HISTORY, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @MethodSource("nonExportableIntents")
  void shouldNotExportRecord(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(ValueType.AGENT_HISTORY, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldCallCreateOnCreatedIntent() {
    // given
    final long historyKey = 42L;
    final var recordValue = buildRecordValue();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.CREATED)
                    .withKey(historyKey)
                    .withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final AgentHistoryDbModel model = modelCaptor.getValue();

    // identity fields mapped from record.getKey(), not value.getAgentHistoryKey()
    assertThat(model.agentHistoryKey()).isEqualTo(historyKey);
    assertThat(model.agentInstanceKey()).isEqualTo(recordValue.getAgentInstanceKey());
    assertThat(model.elementInstanceKey()).isEqualTo(recordValue.getElementInstanceKey());
    assertThat(model.processInstanceKey()).isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(model.rootProcessInstanceKey()).isEqualTo(recordValue.getRootProcessInstanceKey());
    assertThat(model.processDefinitionId()).isEqualTo(recordValue.getBpmnProcessId());
    assertThat(model.processDefinitionKey()).isEqualTo(recordValue.getProcessDefinitionKey());
    assertThat(model.tenantId()).isEqualTo(recordValue.getTenantId());
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());
    assertThat(model.jobKey()).isEqualTo(recordValue.getJobKey());
    assertThat(model.jobLease()).isEqualTo(recordValue.getJobLease());
    assertThat(model.iteration()).isEqualTo(recordValue.getIteration());

    // role
    assertThat(model.role().name()).isEqualTo(recordValue.getRole().name());

    // commit status derived from intent
    assertThat(model.commitStatus()).isEqualTo(AgentInstanceHistoryCommitStatus.PENDING);

    // producedAt taken from value (non-zero)
    assertThat(model.producedAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(recordValue.getProducedAt())));

    // metrics
    assertThat(model.inputTokens()).isEqualTo(recordValue.getMetrics().getInputTokens());
    assertThat(model.outputTokens()).isEqualTo(recordValue.getMetrics().getOutputTokens());
    assertThat(model.durationMs()).isEqualTo(recordValue.getMetrics().getDurationMs());

    // content and tool calls mapped
    assertThat(model.content()).isNotNull();
    assertThat(model.toolCalls()).isNotNull();
  }

  @Test
  void shouldCallUpdateCommitStatusOnCommittedIntent() {
    // given
    final long historyKey = 43L;
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.COMMITTED)
                    .withKey(historyKey)
                    .withValue(buildRecordValue()));

    // when
    handler.export(record);

    // then
    verify(writer).updateCommitStatus(modelCaptor.capture());
    assertThat(modelCaptor.getValue().agentHistoryKey()).isEqualTo(historyKey);
    assertThat(modelCaptor.getValue().commitStatus())
        .isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
  }

  @Test
  void shouldCallUpdateCommitStatusOnDiscardedIntent() {
    // given
    final long historyKey = 44L;
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.DISCARDED)
                    .withKey(historyKey)
                    .withValue(buildRecordValue()));

    // when
    handler.export(record);

    // then
    verify(writer).updateCommitStatus(modelCaptor.capture());
    assertThat(modelCaptor.getValue().agentHistoryKey()).isEqualTo(historyKey);
    assertThat(modelCaptor.getValue().commitStatus())
        .isEqualTo(AgentInstanceHistoryCommitStatus.DISCARDED);
  }

  @Test
  void shouldMapIterationToNullWhenNotPositive() {
    // given — zero and negative values
    final var zeroIteration =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withIteration(0)
            .build();
    final var negativeIteration =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withIteration(-1)
            .build();

    final Record<AgentHistoryRecordValue> zeroRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(50L).withValue(zeroIteration));
    final Record<AgentHistoryRecordValue> negativeRecord =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r ->
                r.withIntent(AgentHistoryIntent.CREATED).withKey(51L).withValue(negativeIteration));

    // when — both records exported
    handler.export(zeroRecord);
    handler.export(negativeRecord);

    // then — both should produce iteration=null
    verify(writer, times(2)).create(modelCaptor.capture());
    assertThat(modelCaptor.getAllValues())
        .extracting(AgentHistoryDbModel::iteration)
        .containsOnly((Integer) null);
  }

  @Test
  void shouldMapEmptyElementIdToNull() {
    // given — tool call with empty elementId
    final var toolCall =
        ImmutableAgentHistoryEmbeddedToolCallValue.builder()
            .withToolCallId("call-1")
            .withToolName("myTool")
            .withElementId("") // empty → should map to null
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withToolCalls(List.of(toolCall))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(52L).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final var mappedToolCall = modelCaptor.getValue().toolCallValues().getFirst();
    assertThat(mappedToolCall.elementId()).isNull();
  }

  @Test
  void shouldMapProducedAtFallbackToRecordTimestamp() {
    // given — producedAt=0 means "not set"; handler falls back to record.getTimestamp()
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withProducedAt(0L)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(53L).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().producedAt())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
  }

  @ParameterizedTest(name = "[{index}] Should map role ''{0}'' to entity role")
  @EnumSource(
      value = AgentHistoryRole.class,
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllRoles(final AgentHistoryRole protocolRole) {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withRole(protocolRole)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(54L).withValue(recordValue));

    // when
    handler.export(record);

    // then — entity role name matches protocol role name
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().role().name()).isEqualTo(protocolRole.name());
  }

  @Test
  void shouldThrowOnUnspecifiedRole() {
    // given
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withRole(AgentHistoryRole.UNSPECIFIED)
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(55L).withValue(recordValue));

    // when / then
    assertThatThrownBy(() -> handler.export(record)).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest(name = "[{index}] Should map content type ''{0}''")
  @EnumSource(
      value = AgentHistoryContentType.class,
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllContentTypes(final AgentHistoryContentType protocolContentType) {
    // given — one content block of the given type
    final var contentBuilder =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(protocolContentType)
            .withText("someText");
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withContent(List.of(contentBuilder.build()))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(56L).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final var mappedItem = modelCaptor.getValue().contentItems().getFirst();
    assertThat(mappedItem.contentType().name()).isEqualTo(protocolContentType.name());
  }

  @Test
  void shouldThrowOnUnspecifiedContentType() {
    // given
    final var content =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(AgentHistoryContentType.UNSPECIFIED)
            .withText("ignored")
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withContent(List.of(content))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(57L).withValue(recordValue));

    // when / then
    assertThatThrownBy(() -> handler.export(record)).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest(name = "Should map commit status for intent: {0}")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"CREATED", "COMMITTED", "DISCARDED"})
  void shouldMapCommitStatusFromIntent(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(intent).withKey(58L).withValue(buildRecordValue()));

    // when
    handler.export(record);

    // then
    if (intent == AgentHistoryIntent.CREATED) {
      verify(writer).create(modelCaptor.capture());
    } else {
      verify(writer).updateCommitStatus(modelCaptor.capture());
    }

    final AgentInstanceHistoryCommitStatus expected =
        switch (intent) {
          case CREATED -> AgentInstanceHistoryCommitStatus.PENDING;
          case COMMITTED -> AgentInstanceHistoryCommitStatus.COMMITTED;
          case DISCARDED -> AgentInstanceHistoryCommitStatus.DISCARDED;
          default -> throw new IllegalStateException("unexpected intent in test: " + intent);
        };
    assertThat(modelCaptor.getValue().commitStatus()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "Should map COMMITTED and DISCARDED records without throwing: {0}")
  @EnumSource(
      value = AgentHistoryIntent.class,
      names = {"COMMITTED", "DISCARDED"})
  void shouldMapCommittedAndDiscardedWithoutThrowing(final AgentHistoryIntent intent) {
    // given
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(intent).withKey(59L).withValue(buildRecordValue()));

    // when / then — no exception thrown
    handler.export(record);
    verify(writer).updateCommitStatus(modelCaptor.capture());
    assertThat(modelCaptor.getValue()).isNotNull();
  }

  @Test
  void shouldMapTextContentType() {
    // given — TEXT content item
    final var content =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(AgentHistoryContentType.TEXT)
            .withText("hello")
            .build();
    final var recordValue =
        ImmutableAgentHistoryRecordValue.builder()
            .from(buildRecordValue())
            .withContent(List.of(content))
            .build();
    final Record<AgentHistoryRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_HISTORY,
            r -> r.withIntent(AgentHistoryIntent.CREATED).withKey(60L).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final var mappedItem = modelCaptor.getValue().contentItems().getFirst();
    assertThat(mappedItem.contentType()).isEqualTo(ContentType.TEXT);
    assertThat(mappedItem.text()).isEqualTo("hello");
    assertThat(mappedItem.documentReference()).isNull();
  }

  /**
   * Builds a fully-populated record value that will not throw during export. In particular: - role
   * is non-UNSPECIFIED - metrics is populated - content has a TEXT item (no UNSPECIFIED content
   * type) - producedAt is > 0
   */
  private AgentHistoryRecordValue buildRecordValue() {
    final var textContent =
        ImmutableAgentHistoryMessageContentValue.builder()
            .withContentType(AgentHistoryContentType.TEXT)
            .withText("test content")
            .build();
    final var toolCall =
        ImmutableAgentHistoryEmbeddedToolCallValue.builder()
            .withToolCallId("call-1")
            .withToolName("myTool")
            .withElementId("el-1")
            .build();
    return ImmutableAgentHistoryRecordValue.builder()
        .withAgentHistoryKey(1L)
        .withAgentInstanceKey(100L)
        .withElementInstanceKey(200L)
        .withProcessInstanceKey(300L)
        .withRootProcessInstanceKey(400L)
        .withBpmnProcessId("myProcess")
        .withProcessDefinitionKey(500L)
        .withTenantId("myTenant")
        .withJobKey(600L)
        .withJobLease("myLease")
        .withIteration(1)
        .withRole(AgentHistoryRole.ASSISTANT)
        .withProducedAt(1_700_000_000_000L)
        .withContent(List.of(textContent))
        .withToolCalls(List.of(toolCall))
        .withMetrics(
            ImmutableAgentHistoryMetricsValue.builder()
                .withInputTokens(10L)
                .withOutputTokens(5L)
                .withDurationMs(100L)
                .build())
        .build();
  }
}
