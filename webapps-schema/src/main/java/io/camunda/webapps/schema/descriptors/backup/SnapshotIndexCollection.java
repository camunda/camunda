/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public record SnapshotIndexCollection(List<String> indices) {
  public SnapshotIndexCollection addIndices(final Collection<String> newIndices) {
    return new SnapshotIndexCollection(
        Stream.concat(indices.stream(), newIndices.stream()).toList());
  }
}
