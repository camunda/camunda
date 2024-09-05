/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface DocumentStoreClient {

  String resolveStoreId(String storeId);

  DocumentStoreClient withStore(String storeId);

  <T> DocumentOperationResponse<T> execute(
      Function<DocumentStore, DocumentOperationResponse<T>> operation);

  <T> CompletableFuture<DocumentOperationResponse<T>> executeAsync(
      Function<DocumentStore, DocumentOperationResponse<T>> operation);
}
