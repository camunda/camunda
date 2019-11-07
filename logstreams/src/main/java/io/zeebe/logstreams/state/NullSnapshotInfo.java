/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import io.zeebe.logstreams.spi.SnapshotInfo;

public class NullSnapshotInfo implements SnapshotInfo {
  @Override
  public long getSnapshotId() {
    return -1;
  }

  @Override
  public int getNumChunks() {
    return -1;
  }
}
