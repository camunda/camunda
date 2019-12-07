/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.versionOffset;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.headerLength;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.log.ReadableFragment;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** Represents the implementation of the logged event. */
public class LoggedEventImpl implements ReadableFragment, LoggedEvent {
  protected DirectBuffer buffer;
  private int fragmentOffset = -1;
  private int messageOffset = -1;

  public void wrap(final DirectBuffer buffer, final int offset) {
    fragmentOffset = offset;
    messageOffset = messageOffset(fragmentOffset);
    this.buffer = buffer;
  }

  @Override
  public int getStreamId() {
    return buffer.getInt(streamIdOffset(fragmentOffset), Protocol.ENDIANNESS);
  }

  @Override
  public int getType() {
    return buffer.getShort(typeOffset(fragmentOffset), Protocol.ENDIANNESS);
  }

  @Override
  public int getVersion() {
    return buffer.getShort(versionOffset(fragmentOffset), Protocol.ENDIANNESS);
  }

  @Override
  public int getMessageOffset() {
    return messageOffset;
  }

  @Override
  public int getMessageLength() {
    return messageLength(buffer.getInt(lengthOffset(fragmentOffset), Protocol.ENDIANNESS));
  }

  @Override
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
  public short getMetadataLength() {
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
    final short metadataLength = getMetadataLength();
    return LogEntryDescriptor.valueOffset(messageOffset, metadataLength);
  }

  @Override
  public int getValueLength() {
    final short metadataLength = getMetadataLength();

    return getMessageLength() - headerLength(metadataLength);
  }

  @Override
  public void readValue(final BufferReader reader) {
    reader.wrap(buffer, getValueOffset(), getValueLength());
  }

  @Override
  public String toString() {
    return "LoggedEvent [type="
        + getType()
        + ", version="
        + getVersion()
        + ", streamId="
        + getStreamId()
        + ", position="
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
  public void write(final MutableDirectBuffer destination, final int offset) {
    destination.putBytes(offset, buffer, fragmentOffset, getLength());
  }
}
