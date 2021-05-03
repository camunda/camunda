/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.protocol.record.RecordMetadataDecoder;
import io.zeebe.protocol.record.RecordMetadataEncoder;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.VersionUtil;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RecordMetadata implements BufferWriter, BufferReader {
  public static final int BLOCK_LENGTH =
      MessageHeaderEncoder.ENCODED_LENGTH + RecordMetadataEncoder.BLOCK_LENGTH;

  private static final VersionInfo CURRENT_BROKER_VERSION =
      VersionInfo.parse(VersionUtil.getVersion());

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final RecordMetadataEncoder encoder = new RecordMetadataEncoder();
  private final RecordMetadataDecoder decoder = new RecordMetadataDecoder();

  private long requestId;
  private ValueType valueType = ValueType.NULL_VAL;
  private RecordType recordType = RecordType.NULL_VAL;
  private short intentValue = Intent.NULL_VAL;
  private Intent intent = null;
  private int requestStreamId;
  private RejectionType rejectionType;
  private final UnsafeBuffer rejectionReason = new UnsafeBuffer(0, 0);

  // always the current version by default
  private int protocolVersion = Protocol.PROTOCOL_VERSION;
  private VersionInfo brokerVersion = CURRENT_BROKER_VERSION;

  public RecordMetadata() {
    reset();
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    reset();

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    recordType = decoder.recordType();
    requestStreamId = decoder.requestStreamId();
    requestId = decoder.requestId();
    protocolVersion = decoder.protocolVersion();
    valueType = decoder.valueType();
    intent = Intent.fromProtocolValue(valueType, decoder.intent());
    rejectionType = decoder.rejectionType();

    brokerVersion =
        Optional.ofNullable(decoder.brokerVersion())
            .map(
                versionDecoder ->
                    new VersionInfo(
                        versionDecoder.majorVersion(),
                        versionDecoder.minorVersion(),
                        versionDecoder.patchVersion()))
            .orElse(VersionInfo.UNKNOWN);

    final int rejectionReasonLength = decoder.rejectionReasonLength();

    if (rejectionReasonLength > 0) {
      offset += headerDecoder.blockLength();
      offset += RecordMetadataDecoder.rejectionReasonHeaderLength();

      rejectionReason.wrap(buffer, offset, rejectionReasonLength);
    }
  }

  @Override
  public int getLength() {
    return BLOCK_LENGTH
        + RecordMetadataEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    headerEncoder.wrap(buffer, offset);

    headerEncoder
        .blockLength(encoder.sbeBlockLength())
        .templateId(encoder.sbeTemplateId())
        .schemaId(encoder.sbeSchemaId())
        .version(encoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    encoder.wrap(buffer, offset);

    encoder
        .recordType(recordType)
        .requestStreamId(requestStreamId)
        .requestId(requestId)
        .protocolVersion(protocolVersion)
        .valueType(valueType)
        .intent(intentValue)
        .rejectionType(rejectionType);

    encoder
        .brokerVersion()
        .majorVersion(brokerVersion.getMajorVersion())
        .minorVersion(brokerVersion.getMinorVersion())
        .patchVersion(brokerVersion.getPatchVersion());

    offset += RecordMetadataEncoder.BLOCK_LENGTH;

    if (rejectionReason.capacity() > 0) {
      encoder.putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
    } else {
      buffer.putInt(offset, 0);
    }
  }

  public long getRequestId() {
    return requestId;
  }

  public RecordMetadata requestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamId;
  }

  public RecordMetadata requestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public RecordMetadata protocolVersion(final int protocolVersion) {
    this.protocolVersion = protocolVersion;
    return this;
  }

  public int getProtocolVersion() {
    return protocolVersion;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public RecordMetadata valueType(final ValueType eventType) {
    valueType = eventType;
    return this;
  }

  public RecordMetadata intent(final Intent intent) {
    this.intent = intent;
    intentValue = intent.value();
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public RecordMetadata recordType(final RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public RecordMetadata rejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public RecordMetadata rejectionReason(final String rejectionReason) {
    final byte[] bytes = rejectionReason.getBytes(StandardCharsets.UTF_8);
    this.rejectionReason.wrap(bytes);
    return this;
  }

  public RecordMetadata rejectionReason(final DirectBuffer buffer) {
    rejectionReason.wrap(buffer);
    return this;
  }

  public String getRejectionReason() {
    return BufferUtil.bufferAsString(rejectionReason);
  }

  public RecordMetadata brokerVersion(final VersionInfo brokerVersion) {
    this.brokerVersion = brokerVersion;
    return this;
  }

  public VersionInfo getBrokerVersion() {
    return brokerVersion;
  }

  public RecordMetadata reset() {
    recordType = RecordType.NULL_VAL;
    requestId = RecordMetadataEncoder.requestIdNullValue();
    requestStreamId = RecordMetadataEncoder.requestStreamIdNullValue();
    protocolVersion = Protocol.PROTOCOL_VERSION;
    valueType = ValueType.NULL_VAL;
    intentValue = Intent.NULL_VAL;
    intent = null;
    rejectionType = RejectionType.NULL_VAL;
    rejectionReason.wrap(0, 0);
    brokerVersion = CURRENT_BROKER_VERSION;
    return this;
  }

  @Override
  public String toString() {
    return "RecordMetadata{"
        + "recordType="
        + recordType
        + ", intentValue="
        + intentValue
        + ", intent="
        + intent
        + ", requestStreamId="
        + requestStreamId
        + ", requestId="
        + requestId
        + ", protocolVersion="
        + protocolVersion
        + ", valueType="
        + valueType
        + ", rejectionType="
        + rejectionType
        + ", rejectionReason="
        + BufferUtil.bufferAsString(rejectionReason)
        + ", brokerVersion="
        + brokerVersion
        + '}';
  }
}
