/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.BatchOperationItemResponse;
import io.camunda.gateway.protocol.model.BatchOperationItemResponse.StateEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SearchQueryResponseMapperTest {

  @Test
  void shouldConvertBatchOperationItemEntity() {
    // given
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(
            "batchOperationKey",
            BatchOperationType.MIGRATE_PROCESS_INSTANCE,
            1234L,
            4321L,
            4320L,
            BatchOperationItemState.COMPLETED,
            OffsetDateTime.parse("2025-01-15T11:53:00Z"),
            "errorMessage");

    // when
    final BatchOperationItemResponse response =
        SearchQueryResponseMapper.toBatchOperationItem(item);

    // then
    assertThat(response.getBatchOperationKey()).isEqualTo("batchOperationKey");
    assertThat(response.getOperationType())
        .isEqualTo(BatchOperationTypeEnum.MIGRATE_PROCESS_INSTANCE);
    assertThat(response.getItemKey()).isEqualTo("1234");
    assertThat(response.getProcessInstanceKey()).isEqualTo("4321");
    assertThat(response.getState()).isEqualTo(StateEnum.COMPLETED);
    assertThat(response.getProcessedDate()).isEqualTo("2025-01-15T11:53:00.000Z");
    assertThat(response.getErrorMessage()).isEqualTo("errorMessage");
  }

  @Test
  void shouldHandleNullFieldsInBatchOperationItemEntity() {
    // given
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(null, null, null, null, null, null, null, null);

    // when
    final BatchOperationItemResponse response =
        SearchQueryResponseMapper.toBatchOperationItem(item);

    // then
    assertThat(response.getBatchOperationKey()).isNull();
    assertThat(response.getOperationType()).isNull();
    assertThat(response.getItemKey()).isNull();
    assertThat(response.getProcessInstanceKey()).isNull();
    assertThat(response.getState()).isNull();
    assertThat(response.getProcessedDate()).isNull();
    assertThat(response.getErrorMessage()).isNull();
  }
}
