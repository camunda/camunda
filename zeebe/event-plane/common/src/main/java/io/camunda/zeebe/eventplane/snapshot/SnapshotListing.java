/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import java.util.List;

/**
 * A log's snapshots in the store, as a listing shows them.
 *
 * @param complete the snapshots with a descriptor, whose files are all there
 * @param withoutDescriptor the ids of snapshot directories without a descriptor: an upload in
 *     progress, or what a stopped upload or deletion left
 */
public record SnapshotListing(List<SnapshotDescriptor> complete, List<String> withoutDescriptor) {

  public SnapshotListing {
    complete = List.copyOf(complete);
    withoutDescriptor = List.copyOf(withoutDescriptor);
  }
}
