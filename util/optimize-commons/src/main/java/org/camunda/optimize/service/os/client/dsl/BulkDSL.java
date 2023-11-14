/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.dsl;

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
