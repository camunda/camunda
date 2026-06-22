/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import io.camunda.eventbridge.protocol.FetchRequestDecoder;
import io.camunda.eventbridge.protocol.FetchRequestEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class FetchRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final FetchRequestEncoder bodyEncoder = new FetchRequestEncoder();
  private final FetchRequestDecoder bodyDecoder = new FetchRequestDecoder();

  private int partitionId;
  private long fromPosition;
  private int maxBytes;
  private int minBytes;
  private long maxWaitMs;

  public int getPartitionId() {
    return partitionId;
  }

  public long getFromPosition() {
    return fromPosition;
  }

  public int getMaxBytes() {
    return maxBytes;
  }

  public int getMinBytes() {
    return minBytes;
  }

  public long getMaxWaitMs() {
    return maxWaitMs;
  }

  public FetchRequest partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public FetchRequest fromPosition(final long fromPosition) {
    this.fromPosition = fromPosition;
    return this;
  }

  public FetchRequest maxBytes(final int maxBytes) {
    this.maxBytes = maxBytes;
    return this;
  }

  public FetchRequest minBytes(final int minBytes) {
    this.minBytes = minBytes;
    return this;
  }

  public FetchRequest maxWaitMs(final long maxWaitMs) {
    this.maxWaitMs = maxWaitMs;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    fromPosition = bodyDecoder.fromPosition();
    maxBytes = bodyDecoder.maxBytes();
    minBytes = bodyDecoder.minBytes();
    maxWaitMs = bodyDecoder.maxWaitMs();
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(partitionId)
        .fromPosition(fromPosition)
        .maxBytes(maxBytes)
        .minBytes(minBytes)
        .maxWaitMs(maxWaitMs);
    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
