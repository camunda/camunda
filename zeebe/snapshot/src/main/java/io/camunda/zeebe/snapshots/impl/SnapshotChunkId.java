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

public record SnapshotChunkId(ByteBuffer id) {
  private static final Charset ID_CHARSET = StandardCharsets.US_ASCII;

  SnapshotChunkId(final String id, final long offset) {
    this(ByteBuffer.wrap((id + "__" + offset).getBytes(ID_CHARSET)).order(Protocol.ENDIANNESS));
  }

  @Override
  public String toString() {
    return ID_CHARSET.decode(id).toString();
  }
}
