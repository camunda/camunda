/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Longs;
import java.util.Collection;
import java.util.OptionalLong;
import java.util.SortedSet;

/**
 * What one partition is restored from.
 *
 * @param backupIds the ids of the backups to restore, in ascending order
 * @param checkpointPosition the position of the resolved checkpoint (a backup or a marker) on this
 *     partition, where its restored log ends. Empty ends the log at the last backup's checkpoint.
 */
public record PartitionRestore(SortedSet<Long> backupIds, OptionalLong checkpointPosition) {

  public PartitionRestore {
    backupIds = ImmutableSortedSet.copyOf(backupIds);
  }

  /** Restores the given backups, up to the last backup's own checkpoint. */
  public static PartitionRestore ofBackups(final long... backupIds) {
    return ofBackups(Longs.asList(backupIds));
  }

  /** Restores the given backups, up to the last backup's own checkpoint. */
  public static PartitionRestore ofBackups(final Collection<Long> backupIds) {
    return new PartitionRestore(ImmutableSortedSet.copyOf(backupIds), OptionalLong.empty());
  }
}
