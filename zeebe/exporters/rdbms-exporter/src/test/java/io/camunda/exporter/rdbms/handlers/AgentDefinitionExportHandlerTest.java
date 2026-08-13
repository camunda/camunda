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
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import io.camunda.db.rdbms.write.service.AgentDefinitionWriter;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentDefinitionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.EnumSet;
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
class AgentDefinitionExportHandlerTest {

  private static final Set<AgentDefinitionIntent> EXPORTABLE_INTENTS =
      EnumSet.of(AgentDefinitionIntent.CREATED);

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private AgentDefinitionWriter writer;
  @Captor private ArgumentCaptor<AgentDefinitionDbModel> modelCaptor;

  private AgentDefinitionExportHandler handler;

  static Stream<AgentDefinitionIntent> exportableIntents() {
    return EXPORTABLE_INTENTS.stream();
  }

  static Stream<AgentDefinitionIntent> nonExportableIntents() {
    return Stream.of(AgentDefinitionIntent.values())
        .filter(Predicate.not(EXPORTABLE_INTENTS::contains));
  }

  @BeforeEach
  void setUp() {
    handler = new AgentDefinitionExportHandler(writer);
  }

  @ParameterizedTest(name = "Should export record with intent: {0}")
  @MethodSource("exportableIntents")
  void shouldExportRecord(final AgentDefinitionIntent intent) {
    // given
    final Record<AgentDefinitionRecordValue> record =
        factory.generateRecord(ValueType.AGENT_DEFINITION, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @MethodSource("nonExportableIntents")
  void shouldNotExportRecord(final AgentDefinitionIntent intent) {
    // given
    final Record<AgentDefinitionRecordValue> record =
        factory.generateRecord(ValueType.AGENT_DEFINITION, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldCallCreateOnCreatedIntent() {
    // given
    final var recordValue = buildRecordValue(123L);
    final Record<AgentDefinitionRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_DEFINITION,
            r -> r.withIntent(AgentDefinitionIntent.CREATED).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final AgentDefinitionDbModel model = modelCaptor.getValue();
    assertThat(model.agentDefinitionKey()).isEqualTo(123L);
    assertThat(model.agentType()).isEqualTo(AgentType.AI_AGENT_TASK);
    assertThat(model.name()).isEqualTo("agentName");
    assertThat(model.elementId()).isEqualTo("elementId");
    assertThat(model.processDefinitionId()).isEqualTo("bpmnProcessId");
    assertThat(model.processDefinitionKey()).isEqualTo(456L);
    assertThat(model.processDefinitionVersion()).isEqualTo(2);
    assertThat(model.processDefinitionVersionTag()).isEqualTo("versionTag");
    assertThat(model.tenantId()).isEqualTo("tenantId");
  }

  @Test
  void shouldMapEmptyProcessDefinitionVersionTagToNull() {
    // given
    final var recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(buildRecordValue(123L))
            .withProcessDefinitionVersionTag("")
            .build();
    final Record<AgentDefinitionRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_DEFINITION,
            r -> r.withIntent(AgentDefinitionIntent.CREATED).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().processDefinitionVersionTag()).isNull();
  }

  @ParameterizedTest(name = "[{index}] Should map protocol agent type ''{0}'' to model agent type")
  @EnumSource(
      value = AgentDefinitionType.class,
      // The broker should never emit UNSPECIFIED;
      // all other protocol agent types must map to an explicit AgentType.
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllProtocolAgentTypes(final AgentDefinitionType protocolAgentType) {
    // given
    final var recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(buildRecordValue(123L))
            .withAgentType(protocolAgentType)
            .build();
    final Record<AgentDefinitionRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_DEFINITION,
            r -> r.withIntent(AgentDefinitionIntent.CREATED).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().agentType())
        .as(
            """
            Protocol agent type '%s' has no explicit mapping in \
            'AgentDefinitionExportHandler.mapAgentType()' — add '%s' to '%s' and handle it \
            explicitly in the switch.\
            """,
            protocolAgentType, protocolAgentType, AgentType.class.getName())
        .isNotNull()
        .extracting(Enum::name)
        .isEqualTo(protocolAgentType.name());
  }

  @Test
  void shouldThrowWhenProtocolAgentTypeIsUnspecified() {
    // given — UNSPECIFIED is not explicitly mapped and must never be emitted by the broker
    final var recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(buildRecordValue(123L))
            .withAgentType(AgentDefinitionType.UNSPECIFIED)
            .build();
    final Record<AgentDefinitionRecordValue> record =
        factory.generateRecord(
            ValueType.AGENT_DEFINITION,
            r -> r.withIntent(AgentDefinitionIntent.CREATED).withValue(recordValue));

    // when / then
    assertThatThrownBy(() -> handler.export(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNSPECIFIED");
  }

  private AgentDefinitionRecordValue buildRecordValue(final long agentDefinitionKey) {
    return ImmutableAgentDefinitionRecordValue.builder()
        .from(factory.generateObject(AgentDefinitionRecordValue.class))
        .withAgentDefinitionKey(agentDefinitionKey)
        .withAgentType(AgentDefinitionType.AI_AGENT_TASK)
        .withName("agentName")
        .withElementId("elementId")
        .withBpmnProcessId("bpmnProcessId")
        .withProcessDefinitionKey(456L)
        .withProcessDefinitionVersion(2)
        .withProcessDefinitionVersionTag("versionTag")
        .withTenantId("tenantId")
        .build();
  }
}
