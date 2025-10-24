/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import org.junit.jupiter.api.Test;

class InitializationContextTest {
  @Test
  void shouldCreateContextWithDefaultPageSize() {
    // given
    final var operation =
        new PersistedBatchOperation()
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("test-cursor");
    // Note: not setting page size, so it should use the default

    // when
    final var context = InitializationContext.fromBatchOperation(operation, 200);

    // then
    assertThat(context.operation()).isEqualTo(operation);
    assertThat(context.currentCursor()).isEqualTo("test-cursor");
    assertThat(context.pageSize()).isEqualTo(200); // should use the default value
    assertThat(context.itemsProcessed()).isZero();
    assertThat(context.hasAppendedChunks()).isFalse();
  }

  @Test
  void shouldCreateContextFromBatchOperationWithNoCursor() {
    // given
    final var operation =
        new PersistedBatchOperation()
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("")
            .setInitializationSearchQueryPageSize(50);

    // when
    final var context = InitializationContext.fromBatchOperation(operation, 100);

    // then
    assertThat(context.operation()).isEqualTo(operation);
    assertThat(context.currentCursor()).isNull();
    assertThat(context.pageSize()).isEqualTo(50);
    assertThat(context.itemsProcessed()).isZero();
    assertThat(context.hasAppendedChunks()).isFalse();
  }

  @Test
  void shouldCreateContextFromBatchOperationWithCursor() {
    // given
    final var operation =
        new PersistedBatchOperation()
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor123")
            .setInitializationSearchQueryPageSize(75);

    // when
    final var context = InitializationContext.fromBatchOperation(operation, 100);

    // then
    assertThat(context.operation()).isEqualTo(operation);
    assertThat(context.currentCursor()).isEqualTo("cursor123");
    assertThat(context.pageSize()).isEqualTo(75);
    assertThat(context.itemsProcessed()).isZero();
    assertThat(context.hasAppendedChunks()).isFalse();
  }

  @Test
  void shouldCreateNextPageContext() {
    // given
    final var operation = new PersistedBatchOperation().setStatus(BatchOperationStatus.CREATED);
    final var originalContext = new InitializationContext(operation, "cursor1", 50, 10, false);

    // when
    final var nextPageContext = originalContext.withNextPage("cursor2", 5, true);

    // then
    assertThat(nextPageContext.operation()).isEqualTo(operation);
    assertThat(nextPageContext.currentCursor()).isEqualTo("cursor2");
    assertThat(nextPageContext.pageSize()).isEqualTo(50);
    assertThat(nextPageContext.itemsProcessed()).isEqualTo(15); // 10 + 5
    assertThat(nextPageContext.hasAppendedChunks()).isTrue();
  }

  @Test
  void shouldCreateContextWithHalvedPageSize() {
    // given
    final var operation = new PersistedBatchOperation().setStatus(BatchOperationStatus.CREATED);
    final var originalContext = new InitializationContext(operation, "cursor1", 100, 25, true);

    // when
    final var halvedContext = originalContext.withHalvedPageSize();

    // then
    assertThat(halvedContext.operation()).isEqualTo(operation);
    assertThat(halvedContext.currentCursor()).isEqualTo("cursor1");
    assertThat(halvedContext.pageSize()).isEqualTo(50); // 100 / 2
    assertThat(halvedContext.itemsProcessed()).isEqualTo(25);
    assertThat(halvedContext.hasAppendedChunks()).isTrue();
  }

  @Test
  void shouldMaintainImmutability() {
    // given
    final var operation = new PersistedBatchOperation().setStatus(BatchOperationStatus.CREATED);
    final var originalContext = new InitializationContext(operation, "cursor1", 50, 10, false);

    // when
    final var nextPageContext = originalContext.withNextPage("cursor2", 5, true);
    final var halvedContext = originalContext.withHalvedPageSize();

    // then - original context should remain unchanged
    assertThat(originalContext.currentCursor()).isEqualTo("cursor1");
    assertThat(originalContext.pageSize()).isEqualTo(50);
    assertThat(originalContext.itemsProcessed()).isEqualTo(10);
    assertThat(originalContext.hasAppendedChunks()).isFalse();

    // and new contexts should have different values
    assertThat(nextPageContext).isNotEqualTo(originalContext);
    assertThat(halvedContext).isNotEqualTo(originalContext);
  }
}
