/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Represent a store, that allows to make copy of existing backups that are targeted at
 * bootstrapping a new partition. A snapshot for bootstrap contains only data that is present in all
 * partitions and does not contain any raft/processing position/index.
 *
 * <p>One backup can be taken at a time, as only one scaling up operation can be done at a time.
 */
public interface BootstrapSnapshotStore {

  /**
   * @return the latest snapshot that was copied if present.
   */
  Optional<PersistedSnapshot> getBootstrapSnapshot();

  /**
   * Makes a copy of the given snapshot to the given destination folder, keeping only the columns
   * needed for the bootstrap process of a new partition. Any raft related data is removed.
   *
   * <p>Only one snapshot for bootstrap can be taken at a time. It will be located into the
   * "bootstrap-snapshots" folder.
   *
   * @param persistedSnapshot the persisted snapshot to copy
   * @param copySnapshot the consumer which copies the snapshot to the destination folder. The first
   *     argument is the path of the source snapshot and the second argument is the destination
   *     folder
   */
  ActorFuture<PersistedSnapshot> copyForBootstrap(
      PersistedSnapshot persistedSnapshot, BiConsumer<Path, Path> copySnapshot);

  /**
   * @return an ActorFuture which completes when all bootstrap snapshots have been deleted
   */
  ActorFuture<Void> deleteBootstrapSnapshots();
}
