/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.exporter.tasks.util.SearchAfter;
import java.util.concurrent.CompletableFuture;

/**
 * This is a package internal interface to help make it slightly easier to hook into the
 * DeleteByIdPipeline class - these methods should not be called outside DeleteByIdPipeline (or
 * tests)
 */
interface DeleteByIdSupport {

  CompletableFuture<DocumentBatch<DocToDelete, SearchAfter>> searchDocumentsToDelete(
      final String indexName, FilterByKeysQuery<?> filter, SearchAfter searchAfter, int batchSize);

  CompletableFuture<Integer> deleteDocBatch(DocumentBatch<DocToDelete, SearchAfter> batch);
}
