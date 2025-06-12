/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.SnapshotChunkResponse;
import org.agrona.MutableDirectBuffer;

public class SnapshotResponseSerializer implements SbeSerializer<SnapshotResponse> {

  private final DeleteSnapshotForBootstrapResponseSerializer deleteSerializer =
      new DeleteSnapshotForBootstrapResponseSerializer();
  private final SnapshotChunkResponseSerializer getSerializer =
      new SnapshotChunkResponseSerializer();

  @Override
  public int size(final SnapshotResponse message) {
    return switch (message) {
      case final DeleteSnapshotForBootstrapResponse request -> deleteSerializer.size(request);
      case final SnapshotChunkResponse request -> getSerializer.size(request);
    };
  }

  @Override
  public int serialize(
      final SnapshotResponse message, final MutableDirectBuffer buffer, final int offset) {
    return switch (message) {
      case final DeleteSnapshotForBootstrapResponse request ->
          deleteSerializer.serialize(request, buffer, offset);
      case final SnapshotChunkResponse request -> getSerializer.serialize(request, buffer, offset);
    };
  }
}
