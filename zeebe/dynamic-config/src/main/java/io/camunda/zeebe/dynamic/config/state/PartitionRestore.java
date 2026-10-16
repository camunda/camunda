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
import org.jspecify.annotations.NullMarked;

/**
 * What one partition is restored from.
 *
 * @param backupIds the ids of the backups to restore, in ascending order
 * @param checkpointPosition where the partition's restored log ends: its position of the checkpoint
 *     the restore resolved to, which lies in the log of the last backup and can be a marker's
 *     between the last two backups. Empty to end the log at the last backup's own checkpoint.
 */
@NullMarked
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
