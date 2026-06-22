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
  private String groupId;
  private String consumerId;
  private long fromPosition;
  private int maxRecords;
  private int serverWaitMs;
  private long epoch;

  public int getPartitionId() {
    return partitionId;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getConsumerId() {
    return consumerId;
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

  public long getEpoch() {
    return epoch;
  }

  public PollRequest partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public PollRequest groupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  public PollRequest consumerId(final String consumerId) {
    this.consumerId = consumerId;
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

  public PollRequest epoch(final long epoch) {
    this.epoch = epoch;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    groupId = bodyDecoder.groupId();
    consumerId = bodyDecoder.consumerId();
    partitionId = bodyDecoder.partitionId();
    fromPosition = bodyDecoder.fromPosition();
    maxRecords = bodyDecoder.maxRecords();
    serverWaitMs = bodyDecoder.serverWaitMs();
    epoch = bodyDecoder.epoch();
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + PollRequestEncoder.groupIdHeaderLength()
        + (groupId == null ? 0 : groupId.length())
        + PollRequestEncoder.consumerIdHeaderLength()
        + (consumerId == null ? 0 : consumerId.length());
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .groupId(groupId)
        .consumerId(consumerId)
        .partitionId(partitionId)
        .fromPosition(fromPosition)
        .maxRecords(maxRecords)
        .serverWaitMs(serverWaitMs)
        .epoch(epoch);

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
