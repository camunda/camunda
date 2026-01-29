/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** Represents the implementation of the logged event. */
public final class LoggedEventImpl implements LoggedEvent {
  private DirectBuffer buffer;
  private int fragmentOffset = -1;
  private int messageOffset = -1;

  public void wrap(final DirectBuffer buffer, final int offset) {
    fragmentOffset = offset;
    messageOffset = offset + DataFrameDescriptor.HEADER_LENGTH;
    this.buffer = buffer;
  }

  public DirectBuffer getBuffer() {
    return buffer;
  }

  public int getFragmentLength() {
    return LogEntryDescriptor.getFragmentLength(buffer, fragmentOffset);
  }

  public int getFragmentOffset() {
    return fragmentOffset;
  }

  @Override
  public boolean shouldSkipProcessing() {
    return LogEntryDescriptor.shouldSkipProcessing(buffer, messageOffset);
  }

  @Override
  public long getPosition() {
    return LogEntryDescriptor.getPosition(buffer, fragmentOffset);
  }

  @Override
  public long getSourceEventPosition() {
    return LogEntryDescriptor.getSourceEventPosition(buffer, messageOffset);
  }

  @Override
  public long getKey() {
    return LogEntryDescriptor.getKey(buffer, messageOffset);
  }

  @Override
  public long getTimestamp() {
    return LogEntryDescriptor.getTimestamp(buffer, messageOffset);
  }

  @Override
  public DirectBuffer getMetadata() {
    return buffer;
  }

  @Override
  public int getMetadataOffset() {
    return LogEntryDescriptor.metadataOffset(messageOffset);
  }

  @Override
  public int getMetadataLength() {
    return LogEntryDescriptor.getMetadataLength(buffer, messageOffset);
  }

  @Override
  public void readMetadata(final BufferReader reader) {
    reader.wrap(buffer, getMetadataOffset(), getMetadataLength());
  }

  @Override
  public DirectBuffer getValueBuffer() {
    return buffer;
  }

  @Override
  public int getValueOffset() {
    final var metadataLength = getMetadataLength();
    return LogEntryDescriptor.valueOffset(messageOffset, metadataLength);
  }

  @Override
  public int getValueLength() {
    final var metadataLength = getMetadataLength();

    return getMessageLength() - LogEntryDescriptor.headerLength(metadataLength);
  }

  @Override
  public void readValue(final BufferReader reader) {
    reader.wrap(buffer, getValueOffset(), getValueLength());
  }

  @Override
  public short getVersion() {
    return LogEntryDescriptor.getVersion(buffer, messageOffset);
  }

  @Override
  public String toString() {
    return "LoggedEvent [position="
        + getPosition()
        + ", key="
        + getKey()
        + ", timestamp="
        + getTimestamp()
        + ", sourceEventPosition="
        + getSourceEventPosition()
        + "]";
  }

  @Override
  public int getLength() {
    return getFragmentLength();
  }

  @Override
  public int write(final MutableDirectBuffer destination, final int offset) {
    destination.putBytes(offset, buffer, fragmentOffset, getLength());
    return getLength();
  }

  private int getMessageLength() {
    return DataFrameDescriptor.messageLength(
        buffer.getInt(DataFrameDescriptor.lengthOffset(fragmentOffset), Protocol.ENDIANNESS));
  }
}
