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
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.protocol.record.value.ImmutableGlobalListenerRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class GlobalListenerCreatedUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "globalListener";
  private final GlobalListenerCreatedUpdatedHandler underTest =
      new GlobalListenerCreatedUpdatedHandler(indexName);

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
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final GlobalListenerIntent intent) {
    // given
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final GlobalListenerIntent intent) {
    // given
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIds(final GlobalListenerIntent intent) {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withGlobalListenerKey(123L)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent).withValue(value));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly("123");
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
  void shouldAddEntityOnFlush() {
    // given
    final GlobalListenerEntity inputEntity =
        new GlobalListenerEntity()
            .setId("123")
            .setListenerId("listener-id")
            .setType("job-type")
            .setRetries(3)
            .setEventTypes(List.of("CREATING", "COMPLETING"))
            .setAfterNonGlobal(true)
            .setPriority(50)
            .setSource(GlobalListenerSource.API)
            .setListenerType(GlobalListenerType.USER_TASK);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldUpdateEntityFromRecord(final GlobalListenerIntent intent) {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withGlobalListenerKey(123L)
            .withId("listener-id")
            .withType("job-type")
            .withRetries(3)
            .withEventTypes(List.of("CREATING", "COMPLETING"))
            .withAfterNonGlobal(true)
            .withPriority(50)
            .withSource(GlobalListenerSource.API)
            .withListenerType(GlobalListenerType.USER_TASK)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent).withValue(value));

    // when
    final GlobalListenerEntity entity = new GlobalListenerEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getListenerId()).isEqualTo("listener-id");
    assertThat(entity.getType()).isEqualTo("job-type");
    assertThat(entity.getRetries()).isEqualTo(3);
    assertThat(entity.getEventTypes()).containsExactly("CREATING", "COMPLETING");
    assertThat(entity.isAfterNonGlobal()).isTrue();
    assertThat(entity.getPriority()).isEqualTo(50);
    assertThat(entity.getSource()).isEqualTo(GlobalListenerSource.API);
    assertThat(entity.getListenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }

  @Test
  void shouldUpdateEntityFromRecordWithDefaultValues() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withGlobalListenerKey(456L)
            .withId("default-listener")
            .withType("default-type")
            .withRetries(GlobalListenerRecordValue.DEFAULT_RETRIES)
            .withEventTypes(List.of("ALL"))
            .withAfterNonGlobal(false)
            .withPriority(GlobalListenerRecordValue.DEFAULT_PRIORITY)
            .withSource(GlobalListenerRecordValue.DEFAULT_SOURCE)
            .withListenerType(GlobalListenerRecordValue.DEFAULT_LISTENER_TYPE)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.CREATED).withValue(value));

    // when
    final GlobalListenerEntity entity = new GlobalListenerEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getListenerId()).isEqualTo("default-listener");
    assertThat(entity.getType()).isEqualTo("default-type");
    assertThat(entity.getRetries()).isEqualTo(3);
    assertThat(entity.getEventTypes()).containsExactly("ALL");
    assertThat(entity.isAfterNonGlobal()).isFalse();
    assertThat(entity.getPriority()).isEqualTo(50);
    assertThat(entity.getSource()).isEqualTo(GlobalListenerSource.CONFIGURATION);
    assertThat(entity.getListenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }
}
