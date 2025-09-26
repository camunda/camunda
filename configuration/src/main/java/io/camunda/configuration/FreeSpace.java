/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.util.unit.DataSize;

public class FreeSpace {
  /**
   * When the free space available is less than this value, this broker rejects all client commands
   * and pause processing.
   */
  private DataSize processing = DataSize.ofGigabytes(2);

  /**
   * When the free space available is less than this value, broker stops receiving replicated
   * events. This value must be less than `...free-space.processing`. It is recommended to configure
   * free space large enough for at least one log segment and one snapshot. This is because a
   * partition needs enough space to take a new snapshot to be able to compact the log segments to
   * make disk space available again.
   */
  private DataSize replication = DataSize.ofGigabytes(1);

  public DataSize getProcessing() {
    return processing;
  }

  public void setProcessing(final DataSize processing) {
    this.processing = processing;
  }

  public DataSize getReplication() {
    return replication;
  }

  public void setReplication(final DataSize replication) {
    this.replication = replication;
  }
}
