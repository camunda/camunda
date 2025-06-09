/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest;
import org.agrona.DirectBuffer;

public class SnapshotRequestDeserializer implements SbeDeserializer<SnapshotRequest> {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final DeleteSnapshotForBootstrapRequestDeserializer
      deleteSnapshotForBootstrapRequestDeserializer =
          new DeleteSnapshotForBootstrapRequestDeserializer();
  private final GetSnapshotChunkDeserializer getSnapshotChunkDeserializer =
      new GetSnapshotChunkDeserializer();

  @Override
  public SnapshotRequest deserialize(
      final DirectBuffer buffer, final int offset, final int capacity) {
    headerDecoder.wrap(buffer, offset);
    return switch (headerDecoder.templateId()) {
      case DeleteSnapshotForBootstrapRequestDecoder.TEMPLATE_ID ->
          deleteSnapshotForBootstrapRequestDeserializer.deserialize(buffer, offset, capacity);
      case GetSnapshotChunkDecoder.TEMPLATE_ID ->
          getSnapshotChunkDeserializer.deserialize(buffer, offset, capacity);
      default ->
          throw new IllegalArgumentException("Unknown template id: " + headerDecoder.templateId());
    };
  }
}
