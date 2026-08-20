/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import org.agrona.DirectBuffer;

public class DeleteSnapshotForBootstrapRequestDeserializer
    implements SbeDeserializer<DeleteSnapshotForBootstrapRequest> {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final DeleteSnapshotForBootstrapRequestDecoder decoder =
      new DeleteSnapshotForBootstrapRequestDecoder();

  @Override
  public DeleteSnapshotForBootstrapRequest deserialize(
      final DirectBuffer buffer, final int offset, final int capacity) {
    decoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    return new DeleteSnapshotForBootstrapRequest(decoder.partition());
  }
}
