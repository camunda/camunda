/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotChunk;
import java.nio.ByteBuffer;

public class DbSnapshotChunk implements SnapshotChunk {
  private final ByteBuffer id;
  private final ByteBuffer data;

  public DbSnapshotChunk(final ByteBuffer id, final ByteBuffer data) {
    this.id = id;
    this.data = data;
  }

  @Override
  public ByteBuffer id() {
    return id;
  }

  @Override
  public ByteBuffer data() {
    return data;
  }
}
