/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.util.ArrayList;
import java.util.List;

public record ArchiveBatch(String finishDate, List<String> ids) {
  public List<ArchiveBatch> chunk(final int chunkSize) {
    final var chunks = new ArrayList<ArchiveBatch>();
    if (ids.isEmpty()) {
      chunks.add(this);
      return chunks;
    }

    List<String> currentIds = new ArrayList<>();

    for (final var id : ids()) {
      if (currentIds.size() >= chunkSize) {
        chunks.add(new ArchiveBatch(finishDate, currentIds));
        currentIds = new ArrayList<>();
      }
      currentIds.add(id);
    }

    if (!currentIds.isEmpty()) {
      chunks.add(new ArchiveBatch(finishDate, currentIds));
    }

    return chunks;
  }
}
