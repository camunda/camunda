/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

abstract class AbstractOperationHandlerTest<R extends RecordValue> {
  protected AbstractOperationHandler<R> underTest;
  protected final ProtocolFactory factory = new ProtocolFactory();
  protected final String indexName = OperationTemplate.INDEX_NAME;
  protected ValueType valueType;

  @Test
  void testGetHandledValueType() {
    assertEquals(underTest.getHandledValueType(), valueType);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(OperationEntity.class);
  }

  @Test
  void testCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  void testGenerateIds() {
    // given
    final long operationReference = 123L;
    final Record<R> record =
        factory.generateRecord(valueType, r -> r.withOperationReference(operationReference));
    // when
    final List<String> ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(operationReference));
  }

  @Test
  void shouldNotGenerateId() {
    // given
    final long operationReference = -1;
    final Record<R> record =
        factory.generateRecord(valueType, r -> r.withOperationReference(operationReference));
    // when
    final List<String> ids = underTest.generateIds(record);
    // then
    assertThat(ids).isEmpty();
  }

  @Test
  void testUpdateEntity() {
    // given
    final Record<R> record = factory.generateRecord(valueType);
    final OperationEntity entity = new OperationEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(entity.getLockOwner()).isNull();
    assertThat(entity.getLockExpirationTime()).isNull();
    assertThat(entity.getCompletedDate()).isNotNull();
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final OperationEntity entity =
        new OperationEntity()
            .withGeneratedId()
            .setState(OperationState.COMPLETED)
            .setLockExpirationTime(null)
            .setLockOwner(null)
            .setCompletedDate(OffsetDateTime.now());

    final BatchRequest mockRequest = mock(BatchRequest.class);
    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(OperationTemplate.STATE, entity.getState());
    expectedUpdateFields.put(OperationTemplate.COMPLETED_DATE, entity.getCompletedDate());
    expectedUpdateFields.put(OperationTemplate.LOCK_OWNER, entity.getLockOwner());
    expectedUpdateFields.put(
        OperationTemplate.LOCK_EXPIRATION_TIME, entity.getLockExpirationTime());

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest).update(indexName, entity.getId(), expectedUpdateFields);
  }

  protected Record<R> generateRecord(final Intent intent) {
    return factory.generateRecord(valueType, r -> r.withIntent(intent));
  }

  protected Record<R> generateRecord(final Intent intent, final R value) {
    return factory.generateRecord(valueType, r -> r.withIntent(intent).withValue(value));
  }
}
