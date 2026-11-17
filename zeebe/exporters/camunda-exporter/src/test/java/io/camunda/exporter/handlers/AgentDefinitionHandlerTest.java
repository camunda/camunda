/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex;
import io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity;
import io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentDefinitionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class AgentDefinitionHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = AgentDefinitionIndex.INDEX_NAME;
  private final AgentDefinitionHandler underTest = new AgentDefinitionHandler(indexName);

  @Test
  void shouldReturnCorrectHandlerMetadata() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.AGENT_DEFINITION);
    assertThat(underTest.getEntityType()).isEqualTo(AgentDefinitionEntity.class);
    assertThat(underTest.getIndexName()).isEqualTo(indexName);
  }

  @ParameterizedTest(name = "[{index}] Should handle ''{0}'' record")
  @EnumSource(
      value = AgentDefinitionIntent.class,
      names = {"CREATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final AgentDefinitionIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isTrue();
  }

  @ParameterizedTest(name = "[{index}] Should not handle ''{0}'' record")
  @EnumSource(
      value = AgentDefinitionIntent.class,
      names = {"CREATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final AgentDefinitionIntent intent) {
    assertThat(underTest.handlesRecord(generateRecord(intent))).isFalse();
  }

  @Test
  void shouldGenerateIdFromAgentDefinitionKey() {
    // given
    final long expectedKey = 123L;
    final AgentDefinitionRecordValue recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(factory.generateObject(AgentDefinitionRecordValue.class))
            .withAgentDefinitionKey(expectedKey)
            .build();
    final Record<AgentDefinitionRecordValue> record =
        generateRecord(AgentDefinitionIntent.CREATED, recordValue);

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedKey));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final AgentDefinitionRecordValue recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(factory.generateObject(AgentDefinitionRecordValue.class))
            .withAgentDefinitionKey(123L)
            .withAgentType(io.camunda.zeebe.protocol.record.value.AgentDefinitionType.AI_AGENT_TASK)
            .withName("agentName")
            .withElementId("elementId")
            .withBpmnProcessId("bpmnProcessId")
            .withProcessDefinitionKey(456L)
            .withProcessDefinitionVersion(2)
            .withProcessDefinitionVersionTag("versionTag")
            .withTenantId("tenantId")
            .build();
    final Record<AgentDefinitionRecordValue> record =
        generateRecord(AgentDefinitionIntent.CREATED, recordValue);

    // when
    final AgentDefinitionEntity entity = new AgentDefinitionEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo("123");
    assertThat(entity.getKey()).isEqualTo(123L);
    assertThat(entity.getAgentType()).isEqualTo(AgentDefinitionType.AI_AGENT_TASK);
    assertThat(entity.getName()).isEqualTo("agentName");
    assertThat(entity.getElementId()).isEqualTo("elementId");
    assertThat(entity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(entity.getProcessDefinitionVersionTag()).isEqualTo("versionTag");
    assertThat(entity.getTenantId()).isEqualTo("tenantId");
  }

  @Test
  void shouldMapEmptyProcessDefinitionVersionTagToNull() {
    // given
    final AgentDefinitionRecordValue recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(factory.generateObject(AgentDefinitionRecordValue.class))
            .withProcessDefinitionVersionTag("")
            .build();
    final Record<AgentDefinitionRecordValue> record =
        generateRecord(AgentDefinitionIntent.CREATED, recordValue);

    // when
    final AgentDefinitionEntity entity = new AgentDefinitionEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getProcessDefinitionVersionTag()).isNull();
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final AgentDefinitionEntity inputEntity = new AgentDefinitionEntity().setId("111");
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(index, inputEntity);
  }

  @ParameterizedTest(name = "[{index}] Should map protocol agent type ''{0}'' to entity agent type")
  @EnumSource(
      value = io.camunda.zeebe.protocol.record.value.AgentDefinitionType.class,
      // The broker should never emit UNSPECIFIED;
      // all other protocol agent types must map to an exporter agent type.
      names = {"UNSPECIFIED"},
      mode = Mode.EXCLUDE)
  void shouldMapAllProtocolAgentTypes(
      final io.camunda.zeebe.protocol.record.value.AgentDefinitionType protocolAgentType) {
    // given
    final AgentDefinitionRecordValue recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(factory.generateObject(AgentDefinitionRecordValue.class))
            .withAgentType(protocolAgentType)
            .build();
    final Record<AgentDefinitionRecordValue> record =
        generateRecord(AgentDefinitionIntent.CREATED, recordValue);
    final AgentDefinitionEntity entity = new AgentDefinitionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getAgentType()).isNotNull();
    assertThat(entity.getAgentType().name())
        .as(
            """
            Protocol agent type '%s' has no explicit mapping in \
            'AgentDefinitionHandler.mapAgentType()' — add '%s' to '%s' and handle it \
            explicitly in the switch.\
            """,
            protocolAgentType, protocolAgentType, AgentDefinitionType.class.getName())
        .isEqualTo(protocolAgentType.name());
  }

  @Test
  void shouldThrowWhenProtocolAgentTypeIsUnspecified() {
    // given — UNSPECIFIED is not explicitly mapped and must never be emitted by the broker
    final AgentDefinitionRecordValue recordValue =
        ImmutableAgentDefinitionRecordValue.builder()
            .from(factory.generateObject(AgentDefinitionRecordValue.class))
            .withAgentType(io.camunda.zeebe.protocol.record.value.AgentDefinitionType.UNSPECIFIED)
            .build();
    final Record<AgentDefinitionRecordValue> record =
        generateRecord(AgentDefinitionIntent.CREATED, recordValue);
    final AgentDefinitionEntity entity = new AgentDefinitionEntity();

    // when - then
    assertThatThrownBy(() -> underTest.updateEntity(record, entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNSPECIFIED");
  }

  private Record<AgentDefinitionRecordValue> generateRecord(final AgentDefinitionIntent intent) {
    return factory.generateRecord(ValueType.AGENT_DEFINITION, r -> r.withIntent(intent));
  }

  private Record<AgentDefinitionRecordValue> generateRecord(
      final AgentDefinitionIntent intent, final AgentDefinitionRecordValue value) {
    return factory.generateRecord(
        ValueType.AGENT_DEFINITION, r -> r.withIntent(intent).withValue(value));
  }
}
