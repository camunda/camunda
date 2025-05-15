/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.GetSnapshotChunk;
import java.util.Optional;
import java.util.UUID;
import org.agrona.DirectBuffer;

public class GetSnapshotChunkDeserializer {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final GetSnapshotChunkDecoder decoder = new GetSnapshotChunkDecoder();

  public GetSnapshotChunk deserialize(
      final DirectBuffer buffer, final int offset, final int capacity) {
    decoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    return new GetSnapshotChunk(
        decoder.partition(),
        new UUID(decoder.transferId().high(), decoder.transferId().low()),
        asOptional(decoder.snapshotIdLength(), decoder.snapshotId()),
        asOptional(decoder.lastChunkNameLength(), decoder.lastChunkName()));
  }

  private Optional<String> asOptional(final int length, final String string) {
    return length > 0 ? Optional.of(string) : Optional.empty();
  }
}
