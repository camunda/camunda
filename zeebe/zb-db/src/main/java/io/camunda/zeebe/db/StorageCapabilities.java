/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * Declares which assumptions the broker may make about a storage backend. The broker is built
 * around local, file-snapshottable storage; each assumption is a named flag so a backend that does
 * not satisfy it is rejected loudly instead of misbehaving later.
 *
 * @param fileSnapshots {@link ZeebeDb#createSnapshot(java.io.File)} and {@link
 *     ZeebeDbFactory#openSnapshotOnlyDb(java.io.File)} are supported, i.e. state can be snapshotted
 *     to and recovered from a directory
 * @param ephemeralRuntime the runtime directory is derived state: it is safe to delete, and is
 *     always rebuilt from a snapshot on recovery
 * @param scopedCopy {@link SnapshotCopy} is supported; the broker's snapshot API handler, which
 *     serves snapshot transfer for bootstrapping and scaling partitions, requires it
 */
public record StorageCapabilities(
    boolean fileSnapshots, boolean ephemeralRuntime, boolean scopedCopy) {

  /**
   * @throws IllegalStateException if the backend cannot snapshot to and recover from a directory
   */
  public void requireFileSnapshots(final String requiredBy) {
    require(fileSnapshots, "fileSnapshots", requiredBy);
  }

  /**
   * @throws IllegalStateException if the runtime directory is not safe to delete and rebuild
   */
  public void requireEphemeralRuntime(final String requiredBy) {
    require(ephemeralRuntime, "ephemeralRuntime", requiredBy);
  }

  /**
   * @throws IllegalStateException if the backend does not support {@link SnapshotCopy}
   */
  public void requireScopedCopy(final String requiredBy) {
    require(scopedCopy, "scopedCopy", requiredBy);
  }

  private static void require(final boolean capability, final String name, final String by) {
    if (!capability) {
      throw new IllegalStateException(
          "%s requires the storage capability '%s', but the configured storage backend does not provide it"
              .formatted(by, name));
    }
  }
}
