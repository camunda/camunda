/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

public class FailedSnapshotReplication extends Exception {

  private long snapshotPosition;

  public FailedSnapshotReplication(long snapshotPosition) {
    this.snapshotPosition = snapshotPosition;
  }

  public long getSnapshotPosition() {
    return snapshotPosition;
  }
}
