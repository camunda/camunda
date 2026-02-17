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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;

class GlobalListenerExportHandlerTest {

  private GlobalListenerWriter globalListenerWriter;
  private GlobalListenerExportHandler handler;

  @BeforeEach
  void setUp() {
    globalListenerWriter = mock(GlobalListenerWriter.class);
    handler = new GlobalListenerExportHandler(globalListenerWriter);
  }

  @ParameterizedTest
  @EnumSource(
      value = GlobalListenerIntent.class,
      names = {"CREATED", "UPDATED", "DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleSupportedIntents(final GlobalListenerIntent intent) {
    // given
    final var record = createMockRecord(intent);

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
    final var record = createMockRecord(intent);

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
    final var record = createMockRecordWithValue(GlobalListenerIntent.CREATED);

    // when
    handler.export(record);

    // then
    final ArgumentCaptor<GlobalListenerDbModel> captor =
        ArgumentCaptor.forClass(GlobalListenerDbModel.class);
    verify(globalListenerWriter).create(captor.capture());
    assertGlobalListenerDbModel(captor.getValue());
  }

  @Test
  void shouldExportUpdatedIntent() {
    // given
    final var record = createMockRecordWithValue(GlobalListenerIntent.UPDATED);

    // when
    handler.export(record);

    // then
    final ArgumentCaptor<GlobalListenerDbModel> captor =
        ArgumentCaptor.forClass(GlobalListenerDbModel.class);
    verify(globalListenerWriter).update(captor.capture());
    assertGlobalListenerDbModel(captor.getValue());
  }

  @Test
  void shouldExportDeletedIntent() {
    // given
    final var record = createMockRecordWithValue(GlobalListenerIntent.DELETED);

    // when
    handler.export(record);

    // then
    final ArgumentCaptor<GlobalListenerDbModel> captor =
        ArgumentCaptor.forClass(GlobalListenerDbModel.class);
    verify(globalListenerWriter).delete(captor.capture());
    assertGlobalListenerDbModel(captor.getValue());
  }

  @Test
  void shouldNotExportUnsupportedIntent() {
    // given
    final var record = createMockRecordWithValue(GlobalListenerIntent.CREATE);

    // when
    handler.export(record);

    // then
    verifyNoInteractions(globalListenerWriter);
  }

  @Test
  void shouldMapRecordCorrectly() {
    // given
    final var record = createMockRecordWithValue(GlobalListenerIntent.CREATED);

    // when
    handler.export(record);

    // then
    final ArgumentCaptor<GlobalListenerDbModel> captor =
        ArgumentCaptor.forClass(GlobalListenerDbModel.class);
    verify(globalListenerWriter).create(captor.capture());

    final var model = captor.getValue();
    assertThat(model.globalListenerKey()).isEqualTo(123L);
    assertThat(model.id()).isEqualTo("listener-id");
    assertThat(model.type()).isEqualTo("job-type");
    assertThat(model.retries()).isEqualTo(3);
    assertThat(model.eventTypes()).containsExactly("CREATING", "COMPLETING");
    assertThat(model.afterNonGlobal()).isTrue();
    assertThat(model.priority()).isEqualTo(50);
    assertThat(model.source()).isEqualTo(GlobalListenerSource.API);
    assertThat(model.listenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }

  @SuppressWarnings("unchecked")
  private Record<GlobalListenerRecordValue> createMockRecord(final GlobalListenerIntent intent) {
    final Record<GlobalListenerRecordValue> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.GLOBAL_LISTENER);
    when(record.getIntent()).thenReturn(intent);
    return record;
  }

  @SuppressWarnings("unchecked")
  private Record<GlobalListenerRecordValue> createMockRecordWithValue(
      final GlobalListenerIntent intent) {
    final Record<GlobalListenerRecordValue> record = mock(Record.class);
    final GlobalListenerRecordValue value = mock(GlobalListenerRecordValue.class);

    when(record.getValueType()).thenReturn(ValueType.GLOBAL_LISTENER);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValue()).thenReturn(value);

    when(value.getGlobalListenerKey()).thenReturn(123L);
    when(value.getId()).thenReturn("listener-id");
    when(value.getType()).thenReturn("job-type");
    when(value.getRetries()).thenReturn(3);
    when(value.getEventTypes()).thenReturn(List.of("CREATING", "COMPLETING"));
    when(value.isAfterNonGlobal()).thenReturn(true);
    when(value.getPriority()).thenReturn(50);
    when(value.getSource())
        .thenReturn(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API);
    when(value.getListenerType())
        .thenReturn(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK);

    return record;
  }

  private void assertGlobalListenerDbModel(final GlobalListenerDbModel model) {
    assertThat(model).isNotNull();
    assertThat(model.globalListenerKey()).isEqualTo(123L);
    assertThat(model.id()).isEqualTo("listener-id");
    assertThat(model.type()).isEqualTo("job-type");
    assertThat(model.retries()).isEqualTo(3);
    assertThat(model.eventTypes()).containsExactly("CREATING", "COMPLETING");
    assertThat(model.afterNonGlobal()).isTrue();
    assertThat(model.priority()).isEqualTo(50);
    assertThat(model.source()).isEqualTo(GlobalListenerSource.API);
    assertThat(model.listenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }
}
