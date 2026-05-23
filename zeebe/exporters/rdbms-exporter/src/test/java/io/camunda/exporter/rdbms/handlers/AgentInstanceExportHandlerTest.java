/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.service.AgentInstanceWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceDefinitionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceLimitsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceToolValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
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
class AgentInstanceExportHandlerTest {

  private static final Set<AgentInstanceIntent> EXPORTABLE_INTENTS =
      EnumSet.of(
          AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private AgentInstanceWriter writer;
  @Captor private ArgumentCaptor<AgentInstanceDbModel> modelCaptor;

  private AgentInstanceExportHandler handler;

  static Stream<AgentInstanceIntent> exportableIntents() {
    return EXPORTABLE_INTENTS.stream();
  }

  static Stream<AgentInstanceIntent> nonExportableIntents() {
    return Stream.of(AgentInstanceIntent.values())
        .filter(Predicate.not(EXPORTABLE_INTENTS::contains));
  }

  @BeforeEach
  void setUp() {
    handler = new AgentInstanceExportHandler(writer);
  }

  @ParameterizedTest(name = "Should export record with intent: {0}")
  @MethodSource("exportableIntents")
  void shouldExportRecord(final AgentInstanceIntent intent) {
    // given
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(ValueType.AGENT_INSTANCE, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @MethodSource("nonExportableIntents")
  void shouldNotExportRecord(final AgentInstanceIntent intent) {
    // given
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(ValueType.AGENT_INSTANCE, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldCallCreateOnCreatedIntent() {
    // given
    final long agentKey = 42L;
    final var recordValue = buildRecordValue(agentKey);
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.CREATED).withKey(agentKey).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final AgentInstanceDbModel model = modelCaptor.getValue();
    // identity
    assertThat(model.agentInstanceKey()).isEqualTo(agentKey);
    assertThat(model.elementId()).isEqualTo("myElement");
    assertThat(model.processInstanceKey()).isEqualTo(100L);
    assertThat(model.rootProcessInstanceKey()).isEqualTo(-1L);
    assertThat(model.processDefinitionId()).isEqualTo("myProcess");
    assertThat(model.processDefinitionKey()).isEqualTo(200L);
    assertThat(model.processDefinitionVersion()).isEqualTo(3);
    assertThat(model.versionTag()).isEqualTo("v1.0");
    assertThat(model.tenantId()).isEqualTo("myTenant");
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());
    // status
    assertThat(model.status()).isEqualTo(AgentInstanceDbModel.AgentInstanceStatus.INITIALIZING);
    // definition
    assertThat(model.model()).isEqualTo("gpt-4o");
    assertThat(model.provider()).isEqualTo("openai");
    assertThat(model.systemPrompt()).isEqualTo("You are helpful.");
    // limits
    assertThat(model.maxTokens()).isEqualTo(1000L);
    assertThat(model.maxModelCalls()).isEqualTo(10);
    assertThat(model.maxToolCalls()).isEqualTo(5);
    // metrics
    assertThat(model.inputTokens()).isEqualTo(100L);
    assertThat(model.outputTokens()).isEqualTo(50L);
    assertThat(model.modelCalls()).isEqualTo(3);
    assertThat(model.toolCalls()).isEqualTo(2);
    // tools
    assertThat(model.tools()).isNotNull();
    // dates
    assertThat(model.creationDate()).isNotNull();
    assertThat(model.lastUpdatedDate()).isEqualTo(model.creationDate());
    assertThat(model.completionDate()).isNull();
    // element instance keys
    assertThat(model.elementInstanceKeys()).containsExactly(300L);
  }

  @Test
  void shouldCallUpdateOnUpdatedIntent() {
    // given
    final long agentKey = 43L;
    final var recordValue = buildRecordValue(agentKey);
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.UPDATED).withKey(agentKey).withValue(recordValue));

    // when
    handler.export(record);

    // then — writer.update() must be called (not writer.create())
    verify(writer).update(modelCaptor.capture());
    final AgentInstanceDbModel model = modelCaptor.getValue();
    assertThat(model.agentInstanceKey()).isEqualTo(agentKey);
    assertThat(model.status()).isEqualTo(AgentInstanceDbModel.AgentInstanceStatus.INITIALIZING);
    assertThat(model.lastUpdatedDate()).isNotNull();
    assertThat(model.completionDate()).isNull();
    // creationDate is only set on CREATED intent
    assertThat(model.creationDate()).isNull();
  }

  @ParameterizedTest(name = "[{index}] Should map protocol status ''{0}'' to entity status")
  @EnumSource(
      value = AgentInstanceStatus.class,
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllProtocolStatuses(final AgentInstanceStatus protocolStatus) {
    // given
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildRecordValue(1L))
            .withStatus(protocolStatus)
            .build();
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r -> r.withIntent(AgentInstanceIntent.CREATED).withKey(1L).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().status())
        .as(
            """
            Protocol status '%s' has no explicit mapping in 'AgentInstanceExportHandler.mapStatus()' \
            and falls back to 'UNKNOWN' — add it explicitly to the switch or exclude it from this test.\
            """,
            protocolStatus)
        .isNotNull()
        .extracting(Enum::name)
        .isEqualTo(protocolStatus.name());
  }

  @Test
  void shouldMapUnexpectedStatusToUnknown() {
    // given — UNSPECIFIED is not explicitly handled in mapStatus() and acts as a proxy
    // for any unexpected/future protocol status that hits the default branch
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildRecordValue(1L))
            .withStatus(AgentInstanceStatus.UNSPECIFIED)
            .build();
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r -> r.withIntent(AgentInstanceIntent.CREATED).withKey(1L).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().status())
        .isEqualTo(AgentInstanceDbModel.AgentInstanceStatus.UNKNOWN);
  }

  @Test
  void shouldSetCompletionDateOnCompletedIntent() {
    // given
    final long agentKey = 44L;
    final var recordValue =
        ImmutableAgentInstanceRecordValue.builder()
            .from(buildRecordValue(agentKey))
            .withStatus(AgentInstanceStatus.COMPLETED)
            .build();
    final Record<AgentInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withIntent(AgentInstanceIntent.COMPLETED)
                    .withKey(agentKey)
                    .withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).update(modelCaptor.capture());
    final AgentInstanceDbModel model = modelCaptor.getValue();
    assertThat(model.completionDate()).isNotNull();
    assertThat(model.lastUpdatedDate()).isEqualTo(model.completionDate());
  }

  private AgentInstanceRecordValue buildRecordValue(final long agentInstanceKey) {
    final var tool =
        ImmutableAgentInstanceToolValue.builder()
            .withName("myTool")
            .withDescription("Does something")
            .withElementId("toolElement")
            .build();
    return ImmutableAgentInstanceRecordValue.builder()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(300L)
        .withElementId("myElement")
        .withProcessInstanceKey(100L)
        .withBpmnProcessId("myProcess")
        .withProcessDefinitionKey(200L)
        .withProcessDefinitionVersion(3)
        .withVersionTag("v1.0")
        .withTenantId("myTenant")
        .withStatus(AgentInstanceStatus.INITIALIZING)
        .withDefinition(
            ImmutableAgentInstanceDefinitionValue.builder()
                .withModel("gpt-4o")
                .withProvider("openai")
                .withSystemPrompt("You are helpful.")
                .build())
        .withLimits(
            ImmutableAgentInstanceLimitsValue.builder()
                .withMaxTokens(1000L)
                .withMaxModelCalls(10)
                .withMaxToolCalls(5)
                .build())
        .withMetrics(
            ImmutableAgentInstanceMetricsValue.builder()
                .withInputTokens(100L)
                .withOutputTokens(50L)
                .withModelCalls(3)
                .withToolCalls(2)
                .build())
        .withTools(List.of(tool))
        .build();
  }
}
