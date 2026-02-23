/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static io.camunda.zeebe.protocol.record.ValueType.JOB;
import static io.camunda.zeebe.protocol.record.ValueType.NULL_VAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.protocol.TestRecord;
import io.camunda.protocol.TestValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExporterBatchWriterTest {
  private ExporterBatchWriter batchWriter;
  private ExportHandler<TestExporterEntity, TestValue> handler;

  @BeforeEach
  void setUp() {
    handler = mock(ExportHandler.class);
    when(handler.getHandledValueType()).thenReturn(NULL_VAL);
    when(handler.getEntityType()).thenReturn(TestExporterEntity.class);
    batchWriter = ExporterBatchWriter.Builder.begin().withHandler(handler).build();
  }

  @Test
  void shouldSkipRecordIfValueTypeHasNoRegisteredHandler() {
    final TestRecord record = new TestRecord(0, JOB);

    // when
    batchWriter.addRecord(record);

    verify(handler, never()).handlesRecord(eq(record));
  }

  @Test
  void shouldNotGenerateOrCacheEntitiesIfHandlingFails() {
    final TestRecord record = new TestRecord(0, NULL_VAL);
    when(handler.handlesRecord(eq(record))).thenReturn(false);

    // when
    batchWriter.addRecord(record);

    verify(handler).handlesRecord(eq(record));

    verify(handler, never()).generateIds(eq(record));
    verify(handler, never()).createNewEntity(anyString());
    verify(handler, never()).updateEntity(any(), any());
    assertThat(batchWriter.getBatchSize()).isEqualTo(0);
  }

  @Test
  void shouldHandleRecordGenerateIdCreateEntityAndAddItToBatch() {
    // given
    final TestRecord record = new TestRecord(0, NULL_VAL);
    final String id = "1";
    final TestExporterEntity entity = new TestExporterEntity().setId(id);
    when(handler.handlesRecord(eq(record))).thenReturn(true);
    when(handler.generateIds(eq(record))).thenReturn(List.of(id));
    when(handler.createNewEntity(eq(id))).thenReturn(entity);

    // when
    batchWriter.addRecord(record);

    // then
    verify(handler).handlesRecord(eq(record));
    verify(handler).generateIds(eq(record));
    verify(handler).createNewEntity(eq(id));
    verify(handler).updateEntity(eq(record), eq(entity));
    assertThat(batchWriter.getBatchSize()).isEqualTo(1);
  }

  @Test
  void shouldLoadEntityFromCacheIfExists() {
    // given
    final TestRecord record = new TestRecord(0, NULL_VAL);
    final String id = "1";
    final TestExporterEntity entity = new TestExporterEntity().setId(id);
    when(handler.handlesRecord(eq(record))).thenReturn(true).thenReturn(true);
    when(handler.generateIds(eq(record))).thenReturn(List.of(id)).thenReturn(List.of(id));
    when(handler.createNewEntity(eq(id))).thenReturn(entity);

    // Add the record
    batchWriter.addRecord(record);
    clearInvocations(handler);

    // when
    // Add it again
    batchWriter.addRecord(record);

    // then
    verify(handler, never()).createNewEntity(eq(id));

    assertThat(batchWriter.getBatchSize()).isEqualTo(1);
  }

  @Test
  void shouldFlushCachedEntitiesToBatchRequestAndExecutesIt() throws PersistenceException {
    // given
    final TestRecord record = new TestRecord(0, NULL_VAL);
    final String id = "1";
    final TestExporterEntity entity = new TestExporterEntity().setId(id);
    when(handler.handlesRecord(eq(record))).thenReturn(true);
    when(handler.generateIds(eq(record))).thenReturn(List.of(id));
    when(handler.createNewEntity(eq(id))).thenReturn(entity);

    batchWriter.addRecord(record);
    assertThat(batchWriter.getBatchSize()).isEqualTo(1);
    assertThat(batchWriter.getEntitiesToFlushSize()).isEqualTo(1);

    // When
    final BatchRequest batchRequest = mock(BatchRequest.class);
    batchWriter.flush(batchRequest);

    // then

    verify(handler).flush(entity, batchRequest);
    verify(batchRequest).execute(any());
    assertThat(batchWriter.getBatchSize()).isEqualTo(0);
    assertThat(batchWriter.getEntitiesToFlushSize()).isEqualTo(0);
  }

  @Test
  void shouldOnlyFlushSameEntityAndHandlerOnce() throws PersistenceException {
    // given
    final TestRecord record = new TestRecord(0, NULL_VAL);
    final String id = "1";
    final TestExporterEntity entity = new TestExporterEntity().setId(id);
    when(handler.handlesRecord(eq(record))).thenReturn(true);
    when(handler.generateIds(eq(record))).thenReturn(List.of(id));
    when(handler.createNewEntity(eq(id))).thenReturn(entity);

    // duplicate the record
    batchWriter.addRecord(record);
    batchWriter.addRecord(record);

    // When
    final BatchRequest batchRequest = mock(BatchRequest.class);
    batchWriter.flush(batchRequest);

    // then
    verify(handler).flush(entity, batchRequest);
    verify(batchRequest).execute(any());
    assertThat(batchWriter.getBatchSize()).isEqualTo(0);
    assertThat(batchWriter.getEntitiesToFlushSize()).isEqualTo(0);
  }
}
