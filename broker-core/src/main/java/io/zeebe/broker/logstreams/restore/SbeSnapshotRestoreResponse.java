/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import io.zeebe.broker.engine.impl.SnapshotChunkImpl;
import io.zeebe.clustering.management.BooleanType;
import io.zeebe.clustering.management.SnapshotRestoreResponseDecoder;
import io.zeebe.clustering.management.SnapshotRestoreResponseEncoder;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import io.zeebe.distributedlog.restore.snapshot.impl.InvalidSnapshotRestoreResponse;
import io.zeebe.distributedlog.restore.snapshot.impl.SuccessSnapshotRestoreResponse;
import io.zeebe.engine.util.SbeBufferWriterReader;
import io.zeebe.logstreams.state.SnapshotChunk;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeSnapshotRestoreResponse
    extends SbeBufferWriterReader<SnapshotRestoreResponseEncoder, SnapshotRestoreResponseDecoder>
    implements SnapshotRestoreResponse {

  private final SnapshotRestoreResponseEncoder encoder;
  private final SnapshotRestoreResponseDecoder decoder;
  private SnapshotRestoreResponse delegate;
  private DirectBuffer snapshotChunkBuffer;

  public SbeSnapshotRestoreResponse() {
    delegate = new InvalidSnapshotRestoreResponse();
    snapshotChunkBuffer = new UnsafeBuffer(0, 0);
    encoder = new SnapshotRestoreResponseEncoder();
    decoder = new SnapshotRestoreResponseDecoder();
    reset();
  }

  public SbeSnapshotRestoreResponse(SnapshotRestoreResponse other) {
    this();
    if (other.isSuccess()) {
      delegate = new SuccessSnapshotRestoreResponse(other.getSnapshotChunk());
      final byte[] bytes = new SnapshotChunkImpl(other.getSnapshotChunk()).toBytes();
      snapshotChunkBuffer.wrap(bytes, 0, bytes.length);
    }
  }

  public SbeSnapshotRestoreResponse(byte[] bytes) {
    this();
    wrap(new UnsafeBuffer(bytes));
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    final boolean valid = decoder.success() == BooleanType.TRUE;
    decoder.wrapSnapshotChunk(snapshotChunkBuffer);
    if (valid) {
      final SnapshotChunkImpl snapshotChunk = new SnapshotChunkImpl();
      snapshotChunk.wrap(snapshotChunkBuffer);
      delegate = new SuccessSnapshotRestoreResponse(snapshotChunk);
    }
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.success(delegate.isSuccess() ? BooleanType.TRUE : BooleanType.FALSE);
    encoder.putSnapshotChunk(snapshotChunkBuffer, 0, snapshotChunkBuffer.capacity());
  }

  @Override
  public SnapshotChunk getSnapshotChunk() {
    return delegate.getSnapshotChunk();
  }

  @Override
  public boolean isSuccess() {
    return delegate.isSuccess();
  }

  public static byte[] serialize(SnapshotRestoreResponse response) {
    return new SbeSnapshotRestoreResponse(response).toBytes();
  }

  @Override
  public String toString() {
    return "SbeSnapshotRestoreResponse{" + "delegate=" + delegate + "} " + super.toString();
  }

  @Override
  protected SnapshotRestoreResponseEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected SnapshotRestoreResponseDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public int getLength() {
    return super.getLength()
        + SnapshotRestoreResponseEncoder.snapshotChunkHeaderLength()
        + snapshotChunkBuffer.capacity();
  }
}
