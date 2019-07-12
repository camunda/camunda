/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import static io.zeebe.clustering.management.RestoreInfoRequestEncoder.backupPositionNullValue;
import static io.zeebe.clustering.management.RestoreInfoRequestEncoder.latestLocalPositionNullValue;

import io.zeebe.clustering.management.RestoreInfoRequestDecoder;
import io.zeebe.clustering.management.RestoreInfoRequestEncoder;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.impl.DefaultRestoreInfoRequest;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeRestoreInfoRequest
    extends SbeBufferWriterReader<RestoreInfoRequestEncoder, RestoreInfoRequestDecoder>
    implements RestoreInfoRequest {
  private final RestoreInfoRequestDecoder decoder;
  private final RestoreInfoRequestEncoder encoder;
  private final DefaultRestoreInfoRequest delegate;

  public SbeRestoreInfoRequest() {
    this.delegate = new DefaultRestoreInfoRequest();
    this.encoder = new RestoreInfoRequestEncoder();
    this.decoder = new RestoreInfoRequestDecoder();
    reset();
  }

  public SbeRestoreInfoRequest(RestoreInfoRequest other) {
    this();
    setBackupPosition(other.getBackupPosition());
    setLatestLocalPosition(other.getLatestLocalPosition());
  }

  public SbeRestoreInfoRequest(byte[] serialized) {
    this();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void reset() {
    super.reset();
    setLatestLocalPosition(latestLocalPositionNullValue());
    setBackupPosition(backupPositionNullValue());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    setLatestLocalPosition(decoder.latestLocalPosition());
    setBackupPosition(decoder.backupPosition());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.backupPosition(getBackupPosition());
    encoder.latestLocalPosition(getLatestLocalPosition());
  }

  @Override
  public long getLatestLocalPosition() {
    return delegate.getLatestLocalPosition();
  }

  public void setLatestLocalPosition(long latestLocalPosition) {
    delegate.setLatestLocalPosition(latestLocalPosition);
  }

  @Override
  public long getBackupPosition() {
    return delegate.getBackupPosition();
  }

  public void setBackupPosition(long backupPosition) {
    delegate.setBackupPosition(backupPosition);
  }

  public static byte[] serialize(RestoreInfoRequest request) {
    return new SbeRestoreInfoRequest(request).toBytes();
  }

  @Override
  protected RestoreInfoRequestEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected RestoreInfoRequestDecoder getBodyDecoder() {
    return decoder;
  }
}
