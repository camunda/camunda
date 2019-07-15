/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import static io.zeebe.clustering.management.SnapshotRestoreRequestEncoder.chunkIdxNullValue;
import static io.zeebe.clustering.management.SnapshotRestoreRequestEncoder.snapshotIdNullValue;

import io.zeebe.clustering.management.SnapshotRestoreRequestDecoder;
import io.zeebe.clustering.management.SnapshotRestoreRequestEncoder;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreRequest;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeSnapshotRestoreRequest
    extends SbeBufferWriterReader<SnapshotRestoreRequestEncoder, SnapshotRestoreRequestDecoder>
    implements SnapshotRestoreRequest {

  private final SnapshotRestoreRequestEncoder encoder;
  private final SnapshotRestoreRequestDecoder decoder;
  private final DefaultSnapshotRestoreRequest delegate;

  public SbeSnapshotRestoreRequest() {
    delegate = new DefaultSnapshotRestoreRequest();
    decoder = new SnapshotRestoreRequestDecoder();
    encoder = new SnapshotRestoreRequestEncoder();
    reset();
  }

  public SbeSnapshotRestoreRequest(SnapshotRestoreRequest request) {
    this();
    setChunkIdx(request.getChunkIdx());
    setSnapshotId(request.getSnapshotId());
  }

  public SbeSnapshotRestoreRequest(byte[] bytes) {
    this();
    wrap(new UnsafeBuffer(bytes));
  }

  @Override
  public void reset() {
    super.reset();
    setSnapshotId(snapshotIdNullValue());
    setChunkIdx(chunkIdxNullValue());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    setSnapshotId(decoder.snapshotId());
    setChunkIdx(decoder.chunkIdx());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.snapshotId(getSnapshotId());
    encoder.chunkIdx(getChunkIdx());
  }

  private void setChunkIdx(int chunkIdx) {
    delegate.setChunkIdx(chunkIdx);
  }

  private void setSnapshotId(long snaphshotId) {
    delegate.setSnapshotId(snaphshotId);
  }

  @Override
  public long getSnapshotId() {
    return delegate.getSnapshotId();
  }

  @Override
  public int getChunkIdx() {
    return delegate.getChunkIdx();
  }

  @Override
  public String toString() {
    return "SbeSnapshotRestoreRequest{" + "delegate=" + delegate + "} " + super.toString();
  }

  @Override
  protected SnapshotRestoreRequestEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected SnapshotRestoreRequestDecoder getBodyDecoder() {
    return decoder;
  }

  public static byte[] serialize(SnapshotRestoreRequest request) {
    return new SbeSnapshotRestoreRequest(request).toBytes();
  }
}
