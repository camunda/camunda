/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ArchiverUtil {

  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final String finishDate,
      final List<String> ids);

  public String getDestinationIndexName(String sourceIndexName, String finishDate);

  public CompletableFuture<Long> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys);

  public abstract CompletableFuture<Long> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys);

  public void setIndexLifeCycle(final String destinationIndexName);
}
