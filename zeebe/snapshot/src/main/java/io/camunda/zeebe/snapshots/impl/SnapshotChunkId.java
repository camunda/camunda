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

  SnapshotChunkId(final String fileName, final long offset) {
    this(
        ByteBuffer.wrap((fileName + "__" + offset).getBytes(ID_CHARSET))
            .order(Protocol.ENDIANNESS));
  }

  public String fileName() {
    final var fileName = ID_CHARSET.decode(id()).toString().split("__")[0];
    id.clear();
    return fileName;
  }

  public long offset() {
    final var offset = Long.valueOf(ID_CHARSET.decode(id()).toString().split("__")[1]);
    id.clear();
    return offset;
  }

  @Override
  public String toString() {
    final String idStr = ID_CHARSET.decode(id).toString();
    id.clear();
    return idStr;
  }
}
