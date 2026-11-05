/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface DeletionRepository extends DeleteByIdSupport {

  /**
   * Used to delete documents in a similar way to a _delete_by_query call but is done via repeated
   * smaller calls to get batches of documents to delete followed by bulk requests to perform the
   * deletes.
   *
   * <p>For large amounts of data (millions/billions of docs) this is a much friendlier approach for
   * ES/OS. It also means that we have no single API call that should take too long to perform.
   *
   * @param indexName the index to delete from (can be a pattern)
   * @param filter the filter to use target the deletes
   * @param executor the executor to runs the individual API calls on
   * @return a future that will complete once all matching documents have been deleted
   */
  CompletableFuture<Void> deleteDocumentsById(
      final String indexName, final FilterByKeysQuery<String> filter, final Executor executor);
}
