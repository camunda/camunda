/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deleter;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

public record ProcessInstanceDeleterBatch(int ordinal, List<Long> rootProcessInstances) {
  public boolean isEmpty() {
    return rootProcessInstances().isEmpty();
  }

  public List<ProcessInstanceDeleterBatch> chunk(final int chunkSize) {
    final var chunks = new ArrayList<ProcessInstanceDeleterBatch>();
    if (isEmpty()) {
      chunks.add(this);
      return chunks;
    }

    return Lists.partition(rootProcessInstances, chunkSize).stream()
        .map(chunk -> new ProcessInstanceDeleterBatch(ordinal, chunk))
        .toList();
  }
}
