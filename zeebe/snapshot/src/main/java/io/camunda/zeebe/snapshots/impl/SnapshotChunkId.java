/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.protocol.Protocol;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class SnapshotChunkId {
  private static final Charset ID_CHARSET = StandardCharsets.US_ASCII;
  private final ByteBuffer id;

  public SnapshotChunkId(final ByteBuffer id) {
    this.id = id;
  }

  SnapshotChunkId(final String id) {
    this.id = ByteBuffer.wrap(id.getBytes(ID_CHARSET)).order(Protocol.ENDIANNESS);
  }

  public ByteBuffer getId() {
    return id;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SnapshotChunkId that = (SnapshotChunkId) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public String toString() {
    return ID_CHARSET.decode(id).toString();
  }
}
