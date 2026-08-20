/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import org.agrona.DirectBuffer;

public class DeleteSnapshotForBootstrapResponseDeserializer
    implements SbeDeserializer<DeleteSnapshotForBootstrapResponse> {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final DeleteSnapshotForBootstrapResponseDecoder decoder =
      new DeleteSnapshotForBootstrapResponseDecoder();

  @Override
  public DeleteSnapshotForBootstrapResponse deserialize(
      final DirectBuffer buffer, final int offset, final int capacity) {
    decoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    return new DeleteSnapshotForBootstrapResponse(decoder.partition());
  }
}
