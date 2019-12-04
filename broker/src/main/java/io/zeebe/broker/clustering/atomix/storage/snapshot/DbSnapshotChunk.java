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
import java.util.Objects;

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

  @Override
  public int hashCode() {
    return Objects.hash(id, data);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final DbSnapshotChunk that = (DbSnapshotChunk) o;
    return id.equals(that.id) && data.equals(that.data);
  }

  @Override
  public String toString() {
    return "DbSnapshotChunk{" + "id=" + id + ", data=" + data + '}';
  }
}
