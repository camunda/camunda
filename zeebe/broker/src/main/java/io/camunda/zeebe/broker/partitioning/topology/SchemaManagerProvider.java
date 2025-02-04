/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.SpringContextHolder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchemaManagerProvider {

  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public static CompletableFuture<SchemaManager> purgeUsingSchemaManager() {
    final CompletableFuture<SchemaManager> future = new CompletableFuture<>();

    tryFetchingBean(future);

    future.thenAccept(
        schemaManager -> {
          // purge using schema manager
          schemaManager.getIndexPrefix();
        });

    return future;
  }

  // need to wait and retry as transitions steps starts before the SpringContextHolder gets its
  // context set
  private static void tryFetchingBean(final CompletableFuture<SchemaManager> future) {
    scheduler.schedule(
        () -> {
          try {
            final SchemaManager schemaManager = SpringContextHolder.getBean(SchemaManager.class);
            future.complete(schemaManager); // Successfully retrieved the bean
          } catch (final Exception e) {
            System.out.println("FAILED TO GET THE BEAN, retrying...");
            tryFetchingBean(future); // Retry non-blockingly
          }
        },
        1,
        TimeUnit.SECONDS);
  }
}
