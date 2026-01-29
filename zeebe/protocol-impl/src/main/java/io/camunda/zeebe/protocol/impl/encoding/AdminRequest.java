/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.AdminRequestDecoder;
import io.camunda.zeebe.protocol.management.AdminRequestEncoder;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class AdminRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final AdminRequestEncoder bodyEncoder = new AdminRequestEncoder();
  private final AdminRequestDecoder bodyDecoder = new AdminRequestDecoder();
  private int brokerId = AdminRequestEncoder.brokerIdNullValue();
  private int partitionId = AdminRequestEncoder.partitionIdNullValue();
  private AdminRequestType type = AdminRequestType.NULL_VAL;
  private long key = AdminRequestEncoder.keyNullValue();
  private byte[] payload = null;
  private boolean hasPayload = false;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    brokerId = bodyDecoder.brokerId();
    partitionId = bodyDecoder.partitionId();
    type = bodyDecoder.type();
    bodyDecoder.getPayload(payload, 0, length - bodyDecoder.limit());
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + AdminRequestEncoder.payloadHeaderLength()
        + (hasPayload ? payload.length : 0);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .brokerId(brokerId)
        .partitionId(partitionId)
        .type(type)
        .key(key);

    if (hasPayload) {
      bodyEncoder.putPayload(payload, 0, payload.length);
    }
    return getLength();
  }

  public int getBrokerId() {
    return brokerId;
  }

  public void setBrokerId(final int brokerId) {
    this.brokerId = brokerId;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public AdminRequestType getType() {
    return type;
  }

  public void setType(final AdminRequestType type) {
    this.type = type;
  }

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public void setPayload(final byte[] payload) {
    this.payload = payload;
    hasPayload = true;
  }
}
