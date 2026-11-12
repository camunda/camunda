/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessDefinitionState;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class ProcessDefinitionSuspensionHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-process";
  private final ProcessDefinitionSuspensionHandler underTest =
      new ProcessDefinitionSuspensionHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_DEFINITION_SUSPENSION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionIntent.class,
      names = {"SUSPENDED", "RESUMED"},
      mode = Mode.INCLUDE)
  void shouldHandleEvent(final ProcessDefinitionSuspensionIntent intent) {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(intent, 1L, List.of());

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionIntent.class,
      names = {"SUSPENDED", "RESUMED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleCommand(final ProcessDefinitionSuspensionIntent intent) {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(intent, 1L, List.of());

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIdsOnlyForSuspendedAndResumedVersions() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(
            ProcessDefinitionSuspensionIntent.SUSPENDED,
            999L,
            List.of(
                version(11L, ProcessDefinitionSuspensionOutcome.SUSPENDED),
                version(12L, ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED),
                version(13L, ProcessDefinitionSuspensionOutcome.DRAINING),
                version(14L, ProcessDefinitionSuspensionOutcome.RESUMED),
                version(15L, ProcessDefinitionSuspensionOutcome.ALREADY_ACTIVE)));

    // when
    final var ids = underTest.generateIds(record);

    // then - the record-level request key 999 is never an id
    assertThat(ids).containsExactly("11", "14");
  }

  @Test
  void shouldSetSuspendedStateOnUpdateEntity() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(
            ProcessDefinitionSuspensionIntent.SUSPENDED,
            1L,
            List.of(version(11L, ProcessDefinitionSuspensionOutcome.SUSPENDED)));
    final ProcessEntity entity = new ProcessEntity().setId("11");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(ProcessDefinitionState.SUSPENDED);
  }

  @Test
  void shouldSetActiveStateOnUpdateEntityForResumedVersion() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(
            ProcessDefinitionSuspensionIntent.RESUMED,
            1L,
            List.of(version(11L, ProcessDefinitionSuspensionOutcome.RESUMED)));
    final ProcessEntity entity = new ProcessEntity().setId("11");
    entity.setState(ProcessDefinitionState.SUSPENDED);

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(ProcessDefinitionState.ACTIVE);
  }

  @Test
  void shouldFlushPartialUpdateWithEntityState() throws Exception {
    // given - DRAINING is set on the entity, not by the handler, to prove flush reads it
    final ProcessEntity entity = new ProcessEntity().setId("789");
    entity.setState(ProcessDefinitionState.DRAINING);
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then
    verify(mockRequest)
        .update(index, "789", Map.of(ProcessIndex.STATE, ProcessDefinitionState.DRAINING));
  }

  @Test
  void shouldReturnIndexName() {
    assertThat(underTest.getIndexName()).isEqualTo(indexName);
  }

  private Record<ProcessDefinitionSuspensionRecordValue> suspensionRecord(
      final ProcessDefinitionSuspensionIntent intent,
      final long requestKey,
      final List<ProcessDefinitionSuspensionResolvedVersionValue> versions) {
    final ProcessDefinitionSuspensionRecordValue value =
        ImmutableProcessDefinitionSuspensionRecordValue.builder()
            .from(factory.generateObject(ImmutableProcessDefinitionSuspensionRecordValue.class))
            .withProcessDefinitionKey(requestKey)
            .withResolvedVersions(versions)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_DEFINITION_SUSPENSION, r -> r.withIntent(intent).withValue(value));
  }

  private ProcessDefinitionSuspensionResolvedVersionValue version(
      final long processDefinitionKey, final ProcessDefinitionSuspensionOutcome outcome) {
    return ImmutableProcessDefinitionSuspensionResolvedVersionValue.builder()
        .withProcessDefinitionKey(processDefinitionKey)
        .withVersion(1)
        .withOutcome(outcome)
        .build();
  }
}
