/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DefaultLogReplicationResponse implements LogReplicationResponse {
  private long toPosition;
  private boolean moreAvailable;
  private byte[] serializedEvents;

  public DefaultLogReplicationResponse() {}

  public DefaultLogReplicationResponse(
      long toPosition, boolean moreAvailable, byte[] serializedEvents) {
    this.toPosition = toPosition;
    this.moreAvailable = moreAvailable;
    this.serializedEvents = serializedEvents;
  }

  @Override
  public long getToPosition() {
    return toPosition;
  }

  public void setToPosition(long toPosition) {
    this.toPosition = toPosition;
  }

  @Override
  public boolean hasMoreAvailable() {
    return moreAvailable;
  }

  @Override
  public byte[] getSerializedEvents() {
    return serializedEvents;
  }

  public void setSerializedEvents(byte[] serializedEvents) {
    this.serializedEvents = serializedEvents;
  }

  public void setMoreAvailable(boolean moreAvailable) {
    this.moreAvailable = moreAvailable;
  }

  public void setSerializedEvents(DirectBuffer buffer, int offset, int length) {
    final DirectBuffer wrapper = new UnsafeBuffer(buffer, offset, length);
    this.serializedEvents = BufferUtil.bufferAsArray(wrapper);
  }

  @Override
  public String toString() {
    return "DefaultLogReplicationResponse{"
        + "toPosition="
        + toPosition
        + ", moreAvailable="
        + moreAvailable
        + ", serializedEvents.length="
        + (serializedEvents == null ? 0 : serializedEvents.length)
        + '}';
  }
}
