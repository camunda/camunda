/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.GetSnapshotChunk;
import org.agrona.MutableDirectBuffer;

public class SnapshotRequestSerializer implements SbeSerializer<SnapshotRequest> {
  private final DeleteSnapshotForBootstrapRequestSerializer deleteSerializer =
      new DeleteSnapshotForBootstrapRequestSerializer();
  private final GetSnapshotChunkSerializer getSerializer = new GetSnapshotChunkSerializer();

  @Override
  public int size(final SnapshotRequest message) {
    return switch (message) {
      case final DeleteSnapshotForBootstrapRequest request -> deleteSerializer.size(request);
      case final GetSnapshotChunk request -> getSerializer.size(request);
    };
  }

  @Override
  public int serialize(
      final SnapshotRequest message, final MutableDirectBuffer buffer, final int offset) {
    return switch (message) {
      case final DeleteSnapshotForBootstrapRequest request ->
          deleteSerializer.serialize(request, buffer, offset);
      case final GetSnapshotChunk request -> getSerializer.serialize(request, buffer, offset);
    };
  }
}
