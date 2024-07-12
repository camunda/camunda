/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.dsl;

import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;

public interface BulkDSL {
  static <R> IndexOperation.Builder<R> indexOperationBuilder(String index) {
    return new IndexOperation.Builder<R>().index(index);
  }

  static BulkOperation bulkOperation(IndexOperation.Builder<?> indexOperationBuilder) {
    return BulkOperation.of(b -> b.index(indexOperationBuilder.build()));
  }
}
