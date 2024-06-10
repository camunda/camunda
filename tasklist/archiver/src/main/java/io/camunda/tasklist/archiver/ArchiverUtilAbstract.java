/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.property.TasklistProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class ArchiverUtilAbstract implements ArchiverUtil {

  private static final String INDEX_NAME_PATTERN = "%s%s";

  @Autowired
  @Qualifier("tasklistArchiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired protected Metrics metrics;

  @Autowired protected TasklistProperties tasklistProperties;

  @Override
  public CompletableFuture<Void> moveDocuments(
      String sourceIndexName, String idFieldName, String finishDate, List<String> ids) {
    final var moveDocumentsFuture = new CompletableFuture<Void>();
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenCompose(
            (ignore) -> {
              setIndexLifeCycle(destinationIndexName);
              return deleteDocuments(sourceIndexName, idFieldName, ids);
            })
        .whenComplete(
            (ignore, e) -> {
              if (e != null) {
                moveDocumentsFuture.completeExceptionally(e);
                return;
              }
              moveDocumentsFuture.complete(null);
            });

    return moveDocumentsFuture;
  }

  @Override
  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  @Override
  public abstract CompletableFuture<Long> deleteDocuments(
      String sourceIndexName, String idFieldName, List<String> processInstanceKeys);
}
