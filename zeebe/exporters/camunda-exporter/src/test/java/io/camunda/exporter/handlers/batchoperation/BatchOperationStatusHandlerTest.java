/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BatchOperationStatusHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-index";
  private final long operationalReference = 42L;

  abstract class AbstractOperationStatusHandlerTest<T extends RecordValue> {

    final AbstractOperationStatusHandler<T> handler;

    AbstractOperationStatusHandlerTest(final AbstractOperationStatusHandler<T> handler) {
      this.handler = handler;
    }

    @Test
    void shouldHandleSuccessRecord() {
      final var record = createSuccessRecord();

      assertThat(handler.handlesRecord(record)).isTrue();
    }

    @Test
    void shouldNotHandleSuccessRecordWithoutOperationReference() {
      final var record =
          ImmutableRecord.<T>builder()
              .from(createSuccessRecord())
              .withOperationReference(operationReferenceNullValue())
              .build();

      assertThat(handler.handlesRecord(record)).isFalse();
    }

    @Test
    void shouldHandleFailureRecord() {
      final var record = createFailureRecord();

      assertThat(handler.handlesRecord(record)).isTrue();
    }

    @Test
    void shouldNotHandleFailureRecordWithoutOperationReference() {
      final var record =
          ImmutableRecord.<T>builder()
              .from(createFailureRecord())
              .withOperationReference(operationReferenceNullValue())
              .build();

      assertThat(handler.handlesRecord(record)).isFalse();
    }

    @Test
    void shouldNotHandleOtherRecord() {
      final var record = factory.generateRecord(ValueType.VARIABLE);
      assertThat(handler.handlesRecord((Record<T>) record)).isFalse();
    }

    @Test
    void shouldUpdateEntityOnSuccess() {
      final var record = createSuccessRecord();
      final var entity = new OperationEntity();

      handler.updateEntity(record, entity);

      assertThat(entity.getState()).isEqualTo(OperationState.COMPLETED);
      assertThat(entity.getCompletedDate()).isNotNull();
      assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    void shouldUpdateEntityOnFailure() {
      final var record = createFailureRecord();
      final var entity = new OperationEntity();

      handler.updateEntity(record, entity);

      assertThat(entity.getState()).isEqualTo(OperationState.FAILED);
      assertThat(entity.getErrorMessage())
          .isEqualTo(record.getRejectionType() + ": " + record.getRejectionReason());
      assertThat(entity.getCompletedDate()).isNull();
    }

    @Test
    void shouldFlushEntityFields() {
      final var entity = new OperationEntity();
      entity.setState(OperationState.COMPLETED);
      entity.setCompletedDate(OffsetDateTime.now());
      entity.setErrorMessage("error message");

      final var mockRequest = mock(BatchRequest.class);

      // when
      handler.flush(entity, mockRequest);

      // then
      verify(mockRequest, times(1))
          .update(
              indexName,
              entity.getId(),
              Map.of(
                  "state", entity.getState(),
                  "completedDate", entity.getCompletedDate(),
                  "errorMessage", entity.getErrorMessage()));
    }

    @Test
    void shouldGenerateCorrectId() {
      final var record = createSuccessRecord();
      final var generatedIds = handler.generateIds(record);

      assertThat(generatedIds)
          .containsExactly(operationalReference + "_" + handler.getItemKey(record));
    }

    @Test
    abstract void shouldExtractCorrectItemKey();

    abstract Record<T> createSuccessRecord();

    abstract Record<T> createFailureRecord();
  }

  @Nested
  class ProcessInstanceModificationOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<ProcessInstanceModificationRecordValue> {

    ProcessInstanceModificationOperationHandlerTest() {
      super(new ProcessInstanceModificationOperationHandler(indexName));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<ProcessInstanceModificationRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          b ->
              b.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                  .withOperationReference(operationalReference));
    }

    @Override
    Record<ProcessInstanceModificationRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          b ->
              b.withRejectionType(RejectionType.NOT_FOUND)
                  .withIntent(ProcessInstanceModificationIntent.MODIFY)
                  .withOperationReference(operationalReference));
    }
  }

  @Nested
  class ProcessInstanceMigrationOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<ProcessInstanceMigrationRecordValue> {

    ProcessInstanceMigrationOperationHandlerTest() {
      super(new ProcessInstanceMigrationOperationHandler(indexName));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<ProcessInstanceMigrationRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MIGRATION,
          b ->
              b.withIntent(ProcessInstanceMigrationIntent.MIGRATED)
                  .withOperationReference(operationalReference));
    }

    @Override
    Record<ProcessInstanceMigrationRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MIGRATION,
          b ->
              b.withRejectionType(RejectionType.NOT_FOUND)
                  .withIntent(ProcessInstanceMigrationIntent.MIGRATE)
                  .withOperationReference(operationalReference));
    }
  }

  @Nested
  class ProcessInstanceCancellationOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<ProcessInstanceRecordValue> {

    ProcessInstanceCancellationOperationHandlerTest() {
      super(new ProcessInstanceCancellationOperationHandler(indexName));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<ProcessInstanceRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE,
          b ->
              b.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                  .withValue(
                      ImmutableProcessInstanceRecordValue.builder()
                          .from(factory.generateObject(ProcessInstanceRecordValue.class))
                          .withBpmnElementType(BpmnElementType.PROCESS)
                          .build())
                  .withOperationReference(operationalReference));
    }

    @Override
    Record<ProcessInstanceRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MIGRATION,
          b ->
              b.withRejectionType(RejectionType.NOT_FOUND)
                  .withValue(
                      ImmutableProcessInstanceRecordValue.builder()
                          .from(factory.generateObject(ProcessInstanceRecordValue.class))
                          .withBpmnElementType(BpmnElementType.PROCESS)
                          .build())
                  .withIntent(ProcessInstanceIntent.CANCEL)
                  .withOperationReference(operationalReference));
    }
  }

  @Nested
  class ResolveIncidentOperationHandlerTest
      extends AbstractOperationStatusHandlerTest<IncidentRecordValue> {

    ResolveIncidentOperationHandlerTest() {
      super(new ResolveIncidentOperationHandler(indexName));
    }

    @Override
    void shouldExtractCorrectItemKey() {
      final var record = createSuccessRecord();
      final var itemKey = handler.getItemKey(record);

      assertThat(itemKey).isEqualTo(record.getValue().getProcessInstanceKey());
    }

    @Override
    Record<IncidentRecordValue> createSuccessRecord() {
      return factory.generateRecord(
          ValueType.INCIDENT,
          b -> b.withIntent(IncidentIntent.RESOLVED).withOperationReference(operationalReference));
    }

    @Override
    Record<IncidentRecordValue> createFailureRecord() {
      return factory.generateRecord(
          ValueType.INCIDENT,
          b ->
              b.withRejectionType(RejectionType.NOT_FOUND)
                  .withIntent(IncidentIntent.RESOLVE)
                  .withOperationReference(operationalReference));
    }
  }
}
