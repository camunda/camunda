/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.GetSnapshotChunk;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.GetSnapshotChunkDecoder;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.GetSnapshotChunkDeserializer;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import org.agrona.DirectBuffer;

public class SnapshotApiRequestReader implements RequestReader<GetSnapshotChunkDecoder> {

  GetSnapshotChunkDeserializer deserializer = new GetSnapshotChunkDeserializer();

  private GetSnapshotChunk request;

  SnapshotApiRequestReader() {
    reset();
  }

  @Override
  public void reset() {
    request = null;
  }

  @Override
  public GetSnapshotChunkDecoder getMessageDecoder() {
    // who cares?
    return null;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    request = deserializer.deserialize(buffer, offset, length);
  }

  public GetSnapshotChunk getRequest() {
    return request;
  }
}
