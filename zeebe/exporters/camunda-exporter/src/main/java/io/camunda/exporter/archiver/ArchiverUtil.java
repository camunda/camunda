/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import static io.camunda.exporter.archiver.Archiver.INDEX_NAME_PATTERN;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ArchiverUtil {

  private final ArchiverRepository archiverRepository;

  public ArchiverUtil(final ArchiverRepository archiverRepository) {
    this.archiverRepository = archiverRepository;
  }

  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final String finishDate,
      final List<Object> ids) {
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);
    return archiverRepository
        .reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenCompose(
            (ignore) -> {
              archiverRepository.setIndexLifeCycle(destinationIndexName);
              return archiverRepository.deleteDocuments(sourceIndexName, idFieldName, ids);
            });
  }

  public String getDestinationIndexName(final String sourceIndexName, final String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }
}
