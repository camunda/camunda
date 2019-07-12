/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import io.zeebe.clustering.management.RestoreInfoResponseDecoder;
import io.zeebe.clustering.management.RestoreInfoResponseEncoder;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.impl.DefaultRestoreInfoResponse;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeRestoreInfoResponse
    extends SbeBufferWriterReader<RestoreInfoResponseEncoder, RestoreInfoResponseDecoder>
    implements RestoreInfoResponse {
  private static final ReplicationTarget[] ENUM_CONSTANTS =
      ReplicationTarget.class.getEnumConstants();

  private final RestoreInfoResponseEncoder encoder;
  private final RestoreInfoResponseDecoder decoder;
  private final DefaultRestoreInfoResponse delegate;

  public SbeRestoreInfoResponse() {
    this.delegate = new DefaultRestoreInfoResponse();
    this.encoder = new RestoreInfoResponseEncoder();
    this.decoder = new RestoreInfoResponseDecoder();
    reset();
  }

  public SbeRestoreInfoResponse(RestoreInfoResponse other) {
    this();
    delegate.setReplicationTarget(other.getReplicationTarget());
    delegate.setSnapshotRestoreInfo(other.getSnapshotRestoreInfo());
  }

  public SbeRestoreInfoResponse(byte[] serialized) {
    this();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    delegate.setReplicationTarget(ENUM_CONSTANTS[decoder.replicationTarget()]);
    delegate.setSnapshotRestoreInfo(decoder.snapshotId(), decoder.numChunks());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.replicationTarget((short) getReplicationTarget().ordinal());
    final SnapshotRestoreInfo snapshotRestoreInfo = getSnapshotRestoreInfo();
    encoder.snapshotId(snapshotRestoreInfo.getSnapshotId());
    encoder.numChunks(snapshotRestoreInfo.getNumChunks());
  }

  @Override
  public ReplicationTarget getReplicationTarget() {
    return delegate.getReplicationTarget();
  }

  @Override
  public SnapshotRestoreInfo getSnapshotRestoreInfo() {
    return delegate.getSnapshotRestoreInfo();
  }

  @Override
  public String toString() {
    return "SbeRestoreInfoResponse{" + "delegate=" + delegate + "} " + super.toString();
  }

  @Override
  protected RestoreInfoResponseEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected RestoreInfoResponseDecoder getBodyDecoder() {
    return decoder;
  }

  public static byte[] serialize(RestoreInfoResponse response) {
    return new SbeRestoreInfoResponse(response).toBytes();
  }
}
