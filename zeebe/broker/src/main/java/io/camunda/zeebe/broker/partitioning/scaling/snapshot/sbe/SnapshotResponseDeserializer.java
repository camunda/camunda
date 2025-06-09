/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse;
import org.agrona.DirectBuffer;

public class SnapshotResponseDeserializer implements SbeDeserializer<SnapshotResponse> {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final DeleteSnapshotForBootstrapResponseDeserializer
      deleteSnapshotForBootstrapRequestDeserializer =
          new DeleteSnapshotForBootstrapResponseDeserializer();
  private final SnapshotChunkResponseDeserializer snapshotChunkResponseDeserializer =
      new SnapshotChunkResponseDeserializer();

  @Override
  public SnapshotResponse deserialize(
      final DirectBuffer buffer, final int offset, final int capacity) {
    headerDecoder.wrap(buffer, offset);
    return switch (headerDecoder.templateId()) {
      case DeleteSnapshotForBootstrapResponseEncoder.TEMPLATE_ID ->
          deleteSnapshotForBootstrapRequestDeserializer.deserialize(buffer, offset, capacity);
      case SnapshotChunkResponseEncoder.TEMPLATE_ID ->
          snapshotChunkResponseDeserializer.deserialize(buffer, offset, capacity);
      default ->
          throw new IllegalArgumentException("Unknown template id: " + headerDecoder.templateId());
    };
  }
}
