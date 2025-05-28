/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.GetSnapshotChunk;
import org.agrona.MutableDirectBuffer;

public class GetSnapshotChunkSerializer {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final GetSnapshotChunkEncoder encoder = new GetSnapshotChunkEncoder();

  public int size(final GetSnapshotChunk response) {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + encoder.sbeBlockLength()
        + 2 * (Integer.BYTES)
        + response.snapshotId().map(String::length).orElse(0)
        + response.lastChunkName().map(String::length).orElse(0);
  }

  public int serialize(
      final GetSnapshotChunk response, final MutableDirectBuffer buffer, final int offset) {
    encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    encoder.partition(response.partitionId());
    encoder
        .transferId()
        .high(response.transferId().getMostSignificantBits())
        .low(response.transferId().getLeastSignificantBits());
    response.snapshotId().ifPresentOrElse(encoder::snapshotId, () -> encoder.snapshotId(""));
    response
        .lastChunkName()
        .ifPresentOrElse(encoder::lastChunkName, () -> encoder.lastChunkName(""));
    return encoder.encodedLength() + headerEncoder.encodedLength();
  }
}
