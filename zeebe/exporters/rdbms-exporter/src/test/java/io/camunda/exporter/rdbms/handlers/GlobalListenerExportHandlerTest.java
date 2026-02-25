/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.service.GlobalListenerWriter;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGlobalListenerRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;

class GlobalListenerExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final GlobalListenerWriter globalListenerWriter = mock(GlobalListenerWriter.class);
  private final GlobalListenerExportHandler handler =
      new GlobalListenerExportHandler(globalListenerWriter);

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"CREATED", "UPDATED", "DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleSupportedIntents(final GlobalListenerIntent intent) {
    // given
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent));

    // when
    final var canExport = handler.canExport(record);

    // then
    assertThat(canExport).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"CREATED", "UPDATED", "DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleUnsupportedIntents(final GlobalListenerIntent intent) {
    // given
    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(ValueType.GLOBAL_LISTENER, r -> r.withIntent(intent));

    // when
    final var canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldNotHandleWrongValueType() {
    // given
    @SuppressWarnings("unchecked")
    final Record<GlobalListenerRecordValue> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(GlobalListenerIntent.CREATED);

    // when
    final var canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
  }

  @Test
  void shouldExportCreatedIntent() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withId("listener-id")
            .withType("job-type")
            .withRetries(3)
            .withEventTypes(List.of("CREATING", "COMPLETING"))
            .withAfterNonGlobal(true)
            .withPriority(50)
            .withSource(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API)
            .withListenerType(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.CREATED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(globalListenerWriter).create(any(GlobalListenerDbModel.class));
  }

  @Test
  void shouldExportUpdatedIntent() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withId("listener-id")
            .withType("job-type")
            .withRetries(3)
            .withEventTypes(List.of("CREATING", "COMPLETING"))
            .withAfterNonGlobal(true)
            .withPriority(50)
            .withSource(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API)
            .withListenerType(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.UPDATED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(globalListenerWriter).update(any(GlobalListenerDbModel.class));
  }

  @Test
  void shouldExportDeletedIntent() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withId("listener-id")
            .withType("job-type")
            .withRetries(3)
            .withEventTypes(List.of("CREATING", "COMPLETING"))
            .withAfterNonGlobal(true)
            .withPriority(50)
            .withSource(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API)
            .withListenerType(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.DELETED).withValue(value));

    // when
    handler.export(record);

    // then
    verify(globalListenerWriter).delete(any(GlobalListenerDbModel.class));
  }

  @Test
  void shouldNotExportUnsupportedIntent() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withId("listener-id")
            .withType("job-type")
            .withRetries(3)
            .withEventTypes(List.of("CREATING", "COMPLETING"))
            .withAfterNonGlobal(true)
            .withPriority(50)
            .withSource(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API)
            .withListenerType(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.CREATE).withValue(value));

    // when
    handler.export(record);

    // then
    verifyNoInteractions(globalListenerWriter);
  }

  @Test
  void shouldMapRecordCorrectly() {
    // given
    final GlobalListenerRecordValue value =
        ImmutableGlobalListenerRecordValue.builder()
            .from(factory.generateObject(GlobalListenerRecordValue.class))
            .withId("listener-id")
            .withType("job-type")
            .withRetries(3)
            .withEventTypes(List.of("CREATING", "COMPLETING"))
            .withAfterNonGlobal(true)
            .withPriority(50)
            .withSource(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API)
            .withListenerType(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK)
            .build();

    final Record<GlobalListenerRecordValue> record =
        factory.generateRecord(
            ValueType.GLOBAL_LISTENER,
            r -> r.withIntent(GlobalListenerIntent.CREATED).withValue(value));

    // when
    handler.export(record);

    // then
    final ArgumentCaptor<GlobalListenerDbModel> captor =
        ArgumentCaptor.forClass(GlobalListenerDbModel.class);
    verify(globalListenerWriter).create(captor.capture());
    final var model = captor.getValue();
    assertThat(model).isNotNull();
    assertThat(model.id()).isEqualTo("USER_TASK-listener-id");
    assertThat(model.listenerId()).isEqualTo("listener-id");
    assertThat(model.type()).isEqualTo("job-type");
    assertThat(model.retries()).isEqualTo(3);
    assertThat(model.eventTypes()).containsExactly("CREATING", "COMPLETING");
    assertThat(model.afterNonGlobal()).isTrue();
    assertThat(model.priority()).isEqualTo(50);
    assertThat(model.source()).isEqualTo(GlobalListenerSource.API);
    assertThat(model.listenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }
}
