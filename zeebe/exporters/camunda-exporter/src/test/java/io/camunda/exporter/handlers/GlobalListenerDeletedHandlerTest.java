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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGlobalListenerRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class GlobalListenerDeletedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "globalListener";
  private final GlobalListenerDeletedHandler underTest =
      new GlobalListenerDeletedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.GLOBAL_LISTENER);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(GlobalListenerEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final GlobalListenerIntent intent) {
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent));
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final GlobalListenerIntent intent) {
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent));
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withGlobalListenerKey(789L)
            .build();
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.DELETED).withValue(value));
    final var idList = underTest.generateIds(record);
    assertThat(idList).containsExactly("789");
  }

  @Test
  void shouldCreateNewEntity() {
    final var result = underTest.createNewEntity("id");
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldDeleteEntityOnFlush() {
    final GlobalListenerEntity inputEntity = new GlobalListenerEntity().setId("123");
    final BatchRequest mockRequest = mock(BatchRequest.class);
    underTest.flush(inputEntity, mockRequest);
    verify(mockRequest, times(1)).delete(indexName, inputEntity.getId());
  }

  @Test
  void shouldNotUpdateEntityForDeletedRecord() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withGlobalListenerKey(999L)
            .build();
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.DELETED).withValue(value));

    // when
    final GlobalListenerEntity entity = new GlobalListenerEntity().setId("999");
    underTest.updateEntity(record, entity);

    // then - entity should remain unchanged (no-op for delete)
    assertThat(entity.getId()).isEqualTo("999");
    assertThat(entity.getListenerId()).isNull();
    assertThat(entity.getType()).isNull();
  }
}
