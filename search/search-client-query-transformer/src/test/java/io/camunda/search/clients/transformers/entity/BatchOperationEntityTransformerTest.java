/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.BatchOperationType;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.webapps.schema.entities.operation.BatchOperationErrorEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchOperationEntityTransformerTest {

  private final BatchOperationEntityTransformer transformer = new BatchOperationEntityTransformer();

  @Test
  void shouldTransformEntityToSearchEntity() {
    // given
    final BatchOperationEntity entity = new BatchOperationEntity();
    entity.setId("1");
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setState(BatchOperationState.ACTIVE);
    entity.setOperationsTotalCount(42);
    entity.setOperationsFailedCount(1);
    entity.setOperationsCompletedCount(41);

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.batchOperationKey()).isEqualTo("1");
    assertThat(searchEntity.state().name()).isEqualTo(BatchOperationState.ACTIVE.name());
    assertThat(searchEntity.operationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(searchEntity.operationsTotalCount()).isEqualTo(42);
    assertThat(searchEntity.operationsFailedCount()).isEqualTo(1);
    assertThat(searchEntity.operationsCompletedCount()).isEqualTo(41);
    assertThat(searchEntity.errors()).isEmpty();
  }

  @Test
  void shouldTransformEntityToSearchEntityWithErrors() {
    // given
    final var entity = new BatchOperationEntity();
    entity.setId("1");
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setState(BatchOperationState.ACTIVE);
    entity.setOperationsTotalCount(42);
    entity.setOperationsFailedCount(1);
    entity.setOperationsCompletedCount(41);

    // add errors
    final var error = new BatchOperationErrorEntity();
    error.setPartitionId(123);
    error.setType("SOME_TYPE");
    error.setMessage("Some error message");
    entity.setErrors(List.of(error));

    // when
    final var searchEntity = transformer.apply(entity);

    // then
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.batchOperationKey()).isEqualTo("1");
    assertThat(searchEntity.state().name()).isEqualTo(BatchOperationState.ACTIVE.name());
    assertThat(searchEntity.operationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(searchEntity.operationsTotalCount()).isEqualTo(42);
    assertThat(searchEntity.operationsFailedCount()).isEqualTo(1);
    assertThat(searchEntity.operationsCompletedCount()).isEqualTo(41);

    // and assert errors
    assertThat(searchEntity.errors()).hasSize(1);
    final var mappedError = searchEntity.errors().get(0);
    assertThat(mappedError.partitionId()).isEqualTo(123);
    assertThat(mappedError.type()).isEqualTo("SOME_TYPE");
    assertThat(mappedError.message()).isEqualTo("Some error message");
  }

  @Test
  void shouldTransformCreatedLegacyEntityToSearchEntity() {
    // given
    final BatchOperationEntity entity = new BatchOperationEntity();
    final String uuid = UUID.randomUUID().toString();
    entity.setId(uuid);
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setOperationsTotalCount(42);
    entity.setOperationsFinishedCount(0);

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity.batchOperationKey()).isEqualTo(uuid);
    assertThat(searchEntity.state())
        .isEqualTo(io.camunda.search.entities.BatchOperationEntity.BatchOperationState.CREATED);
    assertThat(searchEntity.operationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(searchEntity.operationsTotalCount()).isEqualTo(42);
    assertThat(searchEntity.operationsCompletedCount()).isEqualTo(0);
  }

  @Test
  void shouldTransformActiveLegacyEntityToSearchEntity() {
    // given
    final BatchOperationEntity entity = new BatchOperationEntity();
    final String uuid = UUID.randomUUID().toString();
    entity.setId(uuid);
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setOperationsTotalCount(42);
    entity.setOperationsFinishedCount(10);

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity.batchOperationKey()).isEqualTo(uuid);
    assertThat(searchEntity.state())
        .isEqualTo(io.camunda.search.entities.BatchOperationEntity.BatchOperationState.ACTIVE);
    assertThat(searchEntity.operationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(searchEntity.operationsTotalCount()).isEqualTo(42);
    assertThat(searchEntity.operationsCompletedCount()).isEqualTo(10);
  }

  @Test
  void shouldTransformCompletedLegacyEntityToSearchEntity() {
    // given
    final BatchOperationEntity entity = new BatchOperationEntity();
    final String uuid = UUID.randomUUID().toString();
    entity.setId(uuid);
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setOperationsTotalCount(42);
    entity.setOperationsFinishedCount(42);
    entity.setEndDate(OffsetDateTime.now());

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity.batchOperationKey()).isEqualTo(uuid);
    assertThat(searchEntity.state())
        .isEqualTo(io.camunda.search.entities.BatchOperationEntity.BatchOperationState.COMPLETED);
    assertThat(searchEntity.operationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(searchEntity.operationsTotalCount()).isEqualTo(42);
    assertThat(searchEntity.operationsCompletedCount()).isEqualTo(42);
  }
}
