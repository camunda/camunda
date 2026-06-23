/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.protocol.PollRequestDecoder;
import io.camunda.eventbridge.protocol.PollRequestEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PollRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final PollRequestEncoder bodyEncoder = new PollRequestEncoder();
  private final PollRequestDecoder bodyDecoder = new PollRequestDecoder();

  private int partitionId;
  private long fromPosition;
  private int maxRecords;
  private int serverWaitMs;

  public int getPartitionId() {
    return partitionId;
  }

  public long getFromPosition() {
    return fromPosition;
  }

  public int getMaxRecords() {
    return maxRecords;
  }

  public int getServerWaitMs() {
    return serverWaitMs;
  }

  public PollRequest partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public PollRequest fromPosition(final long fromPosition) {
    this.fromPosition = fromPosition;
    return this;
  }

  public PollRequest maxRecords(final int maxRecords) {
    this.maxRecords = maxRecords;
    return this;
  }

  public PollRequest serverWaitMs(final int serverWaitMs) {
    this.serverWaitMs = serverWaitMs;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    fromPosition = bodyDecoder.fromPosition();
    maxRecords = bodyDecoder.maxRecords();
    serverWaitMs = bodyDecoder.serverWaitMs();
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
        .maxRecords(maxRecords)
        .serverWaitMs(serverWaitMs);

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
