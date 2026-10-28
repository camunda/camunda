/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage.disk;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.unit.DataSize;

@NullMarked
public class UsedSpace {

  /**
   * When the disk space used by a physical tenant's data on this broker exceeds this value, the
   * broker rejects the tenant's client commands and pauses its processing. Other physical tenants
   * are not affected. Must be greater than the log segment size. Not set by default, which means
   * the tenant is only bounded by the free space of the volume.
   */
  private @Nullable DataSize processing;

  /**
   * When the disk space used by a physical tenant's data on this broker would exceed this value,
   * the broker stops receiving replicated events for the tenant. Must be greater than
   * `...used-space.processing`, so that the tenant keeps room to take a snapshot and compact its
   * log. Not set by default.
   */
  private @Nullable DataSize replication;

  public @Nullable DataSize getProcessing() {
    return processing;
  }

  public void setProcessing(final @Nullable DataSize processing) {
    this.processing = processing;
  }

  public @Nullable DataSize getReplication() {
    return replication;
  }

  public void setReplication(final @Nullable DataSize replication) {
    this.replication = replication;
  }
}
