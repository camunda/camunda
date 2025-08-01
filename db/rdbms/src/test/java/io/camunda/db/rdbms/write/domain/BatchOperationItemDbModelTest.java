/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import org.junit.jupiter.api.Test;

class BatchOperationItemDbModelTest {

  @Test
  void shouldTruncateErrorMessage() {
    final BatchOperationItemDbModel truncatedMessage =
        new BatchOperationItemDbModel(
                "batchId", 123L, 456L, BatchOperationItemState.ACTIVE, null, "errorMessage")
            .truncateErrorMessage(10, null);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(10);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("errorMessa");
  }

  @Test
  void shouldTruncateErrorMessageBytes() {
    final BatchOperationItemDbModel truncatedMessage =
        new BatchOperationItemDbModel(
                "batchId", 123L, 456L, BatchOperationItemState.ACTIVE, null, "ääääääääää")
            .truncateErrorMessage(50, 5);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(2);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("ää");
  }
}
