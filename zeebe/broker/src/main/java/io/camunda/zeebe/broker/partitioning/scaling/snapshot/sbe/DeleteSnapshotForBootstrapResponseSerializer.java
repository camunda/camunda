/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import org.agrona.MutableDirectBuffer;

public class DeleteSnapshotForBootstrapResponseSerializer
    implements SbeSerializer<DeleteSnapshotForBootstrapResponse> {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final DeleteSnapshotForBootstrapResponseEncoder encoder =
      new DeleteSnapshotForBootstrapResponseEncoder();

  @Override
  public int size(final DeleteSnapshotForBootstrapResponse response) {
    return MessageHeaderEncoder.ENCODED_LENGTH + encoder.sbeBlockLength();
  }

  @Override
  public int serialize(
      final DeleteSnapshotForBootstrapResponse response,
      final MutableDirectBuffer buffer,
      final int offset) {
    encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    encoder.partition(response.partitionId());
    return encoder.encodedLength() + headerEncoder.encodedLength();
  }
}
