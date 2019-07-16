/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import static io.zeebe.clustering.management.LogReplicationRequestEncoder.fromPositionNullValue;
import static io.zeebe.clustering.management.LogReplicationRequestEncoder.toPositionNullValue;

import io.zeebe.clustering.management.BooleanType;
import io.zeebe.clustering.management.LogReplicationRequestDecoder;
import io.zeebe.clustering.management.LogReplicationRequestEncoder;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationRequest;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeLogReplicationRequest
    extends SbeBufferWriterReader<LogReplicationRequestEncoder, LogReplicationRequestDecoder>
    implements LogReplicationRequest {
  private final LogReplicationRequestEncoder encoder;
  private final LogReplicationRequestDecoder decoder;
  private final DefaultLogReplicationRequest delegate;

  public SbeLogReplicationRequest() {
    this.delegate = new DefaultLogReplicationRequest();
    this.encoder = new LogReplicationRequestEncoder();
    this.decoder = new LogReplicationRequestDecoder();
    reset();
  }

  public SbeLogReplicationRequest(LogReplicationRequest other) {
    this();
    this.setFromPosition(other.getFromPosition());
    this.setToPosition(other.getToPosition());
    this.setIncludeFromPosition(other.includeFromPosition());
  }

  public SbeLogReplicationRequest(byte[] serialized) {
    this();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    delegate.setFromPosition(decoder.fromPosition());
    delegate.setToPosition(decoder.toPosition());
    delegate.setIncludeFromPosition(decoder.includeFromPosition() == BooleanType.TRUE);
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder
        .fromPosition(delegate.getFromPosition())
        .toPosition(delegate.getToPosition())
        .includeFromPosition(delegate.includeFromPosition() ? BooleanType.TRUE : BooleanType.FALSE);
  }

  @Override
  public void reset() {
    super.reset();
    delegate.setFromPosition(fromPositionNullValue());
    delegate.setToPosition(toPositionNullValue());
    delegate.setIncludeFromPosition(false);
  }

  @Override
  public boolean includeFromPosition() {
    return delegate.includeFromPosition();
  }

  public void setIncludeFromPosition(boolean includeFromPosition) {
    delegate.setIncludeFromPosition(includeFromPosition);
  }

  @Override
  public long getFromPosition() {
    return delegate.getFromPosition();
  }

  public void setFromPosition(long fromPosition) {
    delegate.setFromPosition(fromPosition);
  }

  @Override
  public long getToPosition() {
    return delegate.getToPosition();
  }

  public void setToPosition(long toPosition) {
    delegate.setToPosition(toPosition);
  }

  public static byte[] serialize(LogReplicationRequest request) {
    return new SbeLogReplicationRequest(request).toBytes();
  }

  @Override
  public String toString() {
    return "SbeLogReplicationRequest{" + "delegate=" + delegate + "} " + super.toString();
  }

  @Override
  protected LogReplicationRequestEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected LogReplicationRequestDecoder getBodyDecoder() {
    return decoder;
  }
}
