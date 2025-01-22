/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.util.SpringContextHolder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManagerProvider {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManagerProvider.class);
  private static final int RETRY_COUNT = 5;

  public static CompletableFuture<SchemaManager> purgeUsingSchemaManager() {
    final CompletableFuture<SchemaManager> future = new CompletableFuture<>();
    tryFetchingBean(future);
    return future;
  }

  // need to wait and retry as transitions steps starts before the SpringContextHolder gets its
  // context set
  private static void tryFetchingBean(final CompletableFuture<SchemaManager> future) {
    try {
      RetryOperation.newBuilder()
          .noOfRetry(RETRY_COUNT)
          .retryOn(NullPointerException.class)
          .delayInterval(1, TimeUnit.SECONDS)
          .message("Fetching SchemaManager bean")
          .retryConsumer(
              () -> {
                LOG.info("Fetching SchemaManager ...");
                final SchemaManager schemaManager =
                    SpringContextHolder.getBean(SchemaManager.class);
                future.complete(schemaManager); // Successfully retrieved the bean
                return true;
              })
          .build()
          .retry();
    } catch (final Exception e) {
      LOG.error("FAILED TO GET THE BEAN, retrying...", e);
      future.completeExceptionally(e);
    }
  }
}
