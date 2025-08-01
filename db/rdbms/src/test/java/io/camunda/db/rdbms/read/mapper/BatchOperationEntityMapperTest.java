/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorDto;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchOperationEntityMapperTest {

  @Test
  void shouldMapDbModelToEntity() {
    final var errorDbModel = new BatchOperationDbModel.BatchOperationErrorDbModel();
    errorDbModel.partitionId(1);
    errorDbModel.type("ERROR_TYPE");
    errorDbModel.message("stacktrace");

    final var dbModel =
        new BatchOperationDbModel.Builder()
            .batchOperationKey("id-1")
            .state(io.camunda.search.entities.BatchOperationEntity.BatchOperationState.ACTIVE)
            .operationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .startDate(OffsetDateTime.parse("2024-07-01T10:00:00Z"))
            .endDate(null)
            .operationsTotalCount(10)
            .operationsFailedCount(2)
            .operationsCompletedCount(8)
            .errors(List.of(errorDbModel))
            .build();

    final var entity = BatchOperationEntityMapper.toEntity(dbModel);

    assertThat(entity.batchOperationKey()).isEqualTo("id-1");
    assertThat(entity.state())
        .isEqualTo(io.camunda.search.entities.BatchOperationEntity.BatchOperationState.ACTIVE);
    assertThat(entity.operationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(entity.startDate()).isEqualTo(OffsetDateTime.parse("2024-07-01T10:00:00Z"));
    assertThat(entity.endDate()).isNull();
    assertThat(entity.operationsTotalCount()).isEqualTo(10);
    assertThat(entity.operationsFailedCount()).isEqualTo(2);
    assertThat(entity.operationsCompletedCount()).isEqualTo(8);
    assertThat(entity.errors()).hasSize(1);

    final var errorEntity = entity.errors().getFirst();
    assertThat(errorEntity.partitionId()).isEqualTo(1);
    assertThat(errorEntity.type()).isEqualTo("ERROR_TYPE");
    assertThat(errorEntity.message()).isEqualTo("stacktrace");
  }

  @Test
  void shouldMapErrorDbModelToErrorEntity() {
    final var errorDbModel = new BatchOperationDbModel.BatchOperationErrorDbModel();
    errorDbModel.partitionId(42);
    errorDbModel.type("SOME_ERROR");
    errorDbModel.message("trace");

    final var errorEntity = BatchOperationEntityMapper.toErrorEntity(errorDbModel);

    assertThat(errorEntity.partitionId()).isEqualTo(42);
    assertThat(errorEntity.type()).isEqualTo("SOME_ERROR");
    assertThat(errorEntity.message()).isEqualTo("trace");
  }

  @Test
  void shouldTruncateBatchOperationErrorMessageWithLargeValue() {
    final var truncatedMessage =
        new BatchOperationErrorDto(1, "errorType", "errorMessage").truncateErrorMessage(10, null);

    assertThat(truncatedMessage.message().length()).isEqualTo(10);
    assertThat(truncatedMessage.message()).isEqualTo("errorMessa");
  }

  @Test
  void shouldTruncateBatchOperationErrorMessageWithLargeBytes() {
    final var truncatedMessage =
        new BatchOperationErrorDto(1, "errorType", "ääääääääää").truncateErrorMessage(50, 5);

    assertThat(truncatedMessage.message().length()).isEqualTo(2);
    assertThat(truncatedMessage.message()).isEqualTo("ää");
  }
}
