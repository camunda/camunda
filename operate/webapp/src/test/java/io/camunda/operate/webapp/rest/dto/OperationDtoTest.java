/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class OperationDtoTest {

  @Test
  void shouldHaveCompletedDateField() {
    final var operation = new OperationDto();
    // Default value is null
    assertThat(operation.getCompletedDate()).isNull();
    operation.setCompletedDate(OffsetDateTime.now());
    assertThat(operation.getCompletedDate()).isInstanceOf(OffsetDateTime.class);
  }

  @Test
  void shouldFillValuesFromOperationEntity() {
    final var operation =
        new OperationDto()
            .fillFrom(
                new OperationEntity()
                    .setId("id")
                    .setType(OperationType.MODIFY_PROCESS_INSTANCE)
                    .setState(OperationState.COMPLETED)
                    .setErrorMessage("errorMessage")
                    .setBatchOperationId("batchOperationId")
                    .setCompletedDate(OffsetDateTime.now()));
    assertThat(operation.getId()).isEqualTo("id");
    assertThat(operation.getType()).isEqualTo(OperationType.MODIFY_PROCESS_INSTANCE);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getErrorMessage()).isEqualTo("errorMessage");
    assertThat(operation.getBatchOperationId()).isEqualTo("batchOperationId");
    assertThat(operation.getCompletedDate()).isInstanceOf(OffsetDateTime.class);
  }
}
