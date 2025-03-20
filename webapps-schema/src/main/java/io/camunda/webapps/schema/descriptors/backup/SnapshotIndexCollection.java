/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SnapshotIndexCollection(List<String> requiredIndices, List<String> skippableIndices) {

  public boolean isEmpty() {
    return requiredIndices.isEmpty() && skippableIndices.isEmpty();
  }

  public static <A extends BackupPriority> SnapshotIndexCollection of(
      final Collection<A> backupPriorities) {
    final var required = new ArrayList<String>(backupPriorities.size());
    final var skippable = new ArrayList<String>();
    for (final var priority : backupPriorities) {
      if (priority.required()) {
        required.add(priority.getFullQualifiedName());
      } else {
        skippable.add(priority.getFullQualifiedName());
      }
    }
    return new SnapshotIndexCollection(
        Collections.unmodifiableList(required), Collections.unmodifiableList(skippable));
  }

  public SnapshotIndexCollection removeSkippableIndices(final Collection<String> indicesToRemove) {
    if (indicesToRemove.isEmpty()) {
      return this;
    }
    return new SnapshotIndexCollection(
        requiredIndices,
        skippableIndices.stream().filter(idx -> !indicesToRemove.contains(idx)).toList());
  }

  public List<String> allIndices() {
    return Stream.concat(requiredIndices.stream(), skippableIndices.stream())
        .collect(Collectors.toList());
  }
}
