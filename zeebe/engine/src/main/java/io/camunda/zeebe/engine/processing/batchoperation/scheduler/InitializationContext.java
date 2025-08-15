/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static com.google.common.base.Strings.emptyToNull;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;

/**
 * Represents the context for initializing a batch operation, including the operation itself,
 * current cursor, page size, number of items processed, and whether chunks have been appended.
 */
public record InitializationContext(
    PersistedBatchOperation operation,
    String currentCursor,
    int pageSize,
    int itemsProcessed,
    boolean hasAppendedChunks) {

  public static InitializationContext fromBatchOperation(
      final PersistedBatchOperation operation, final int defaultPageSize) {
    return new InitializationContext(
        operation,
        emptyToNull(operation.getInitializationSearchCursor()),
        operation.getInitializationSearchQueryPageSize(defaultPageSize),
        0,
        false);
  }

  public InitializationContext withNextPage(
      final String newCursor, final int newItems, final boolean appended) {
    return new InitializationContext(
        operation, newCursor, pageSize, itemsProcessed + newItems, appended);
  }

  public InitializationContext withHalvedPageSize() {
    return new InitializationContext(
        operation, currentCursor, pageSize / 2, itemsProcessed, hasAppendedChunks);
  }
}
