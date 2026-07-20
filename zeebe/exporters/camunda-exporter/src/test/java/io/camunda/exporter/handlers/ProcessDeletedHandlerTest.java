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
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class ProcessDeletedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-process";
  private final ProcessDeletedHandler underTest = new ProcessDeletedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessIntent.class,
      names = {"DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final ProcessIntent intent) {
    // given
    final Record<Process> record =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessIntent.class,
      names = {"DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final ProcessIntent intent) {
    // given
    final Record<Process> record =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIdFromProcessDefinitionKey() {
    // given
    final long expectedKey = 123L;
    final Process value =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(expectedKey)
            .build();
    final Record<Process> record =
        factory.generateRecord(
            ValueType.PROCESS, r -> r.withIntent(ProcessIntent.DELETED).withValue(value));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(expectedKey));
  }

  @Test
  void shouldCreateNewEntityWithId() {
    // when
    final var entity = underTest.createNewEntity("456");

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo("456");
    assertThat(entity.getIsDeleted()).isFalse();
  }

  @Test
  void shouldSetIsDeletedTrueOnUpdateEntity() {
    // given
    final Record<Process> record =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(ProcessIntent.DELETED));
    final ProcessEntity entity = new ProcessEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getIsDeleted()).isTrue();
  }

  @Test
  void shouldFlushPartialUpdateWithIsDeletedTrue() throws Exception {
    // given
    final ProcessEntity entity = new ProcessEntity().setId("789");
    final TargetIndex index = mock(TargetIndex.class);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, mockRequest);

    // then
    verify(mockRequest).update(index, "789", Map.of(ProcessIndex.IS_DELETED, true));
  }
}
