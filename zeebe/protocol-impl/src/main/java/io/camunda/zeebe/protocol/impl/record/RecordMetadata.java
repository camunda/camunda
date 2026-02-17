/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.AgentInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RecordMetadata implements BufferWriter, BufferReader {
  public static final int BLOCK_LENGTH =
      MessageHeaderEncoder.ENCODED_LENGTH + RecordMetadataEncoder.BLOCK_LENGTH;
  public static final int DEFAULT_RECORD_VERSION = 1;

  public static final VersionInfo CURRENT_BROKER_VERSION =
      VersionInfo.parse(VersionUtil.getVersion());

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final RecordMetadataEncoder encoder = new RecordMetadataEncoder();
  private final RecordMetadataDecoder decoder = new RecordMetadataDecoder();

  private RecordType recordType = RecordType.NULL_VAL;
  private ValueType valueType = ValueType.NULL_VAL;
  private Intent intent = null;
  private long requestId;
  private short intentValue = Intent.NULL_VAL;
  private int requestStreamId;
  private final AuthInfo authorization = new AuthInfo();
  private RejectionType rejectionType;
  private final UnsafeBuffer rejectionReason = new UnsafeBuffer(0, 0);
  private AgentInfo agent;

  // always the current version by default
  private int protocolVersion = Protocol.PROTOCOL_VERSION;
  private VersionInfo brokerVersion = CURRENT_BROKER_VERSION;
  private int recordVersion = DEFAULT_RECORD_VERSION;
  private long operationReference;
  private long batchOperationReference;

  public RecordMetadata() {
    reset();
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    reset();

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    // working with fixed-length fields
    recordType = decoder.recordType();
    requestStreamId = decoder.requestStreamId();
    requestId = decoder.requestId();
    protocolVersion = decoder.protocolVersion();
    valueType = decoder.valueType();
    intent = Intent.fromProtocolValue(valueType, decoder.intent());
    rejectionType = decoder.rejectionType();
    operationReference = decoder.operationReference();
    batchOperationReference = decoder.batchOperationReference();

    brokerVersion =
        Optional.ofNullable(decoder.brokerVersion())
            .map(
                versionDecoder ->
                    new VersionInfo(
                        versionDecoder.majorVersion(),
                        versionDecoder.minorVersion(),
                        versionDecoder.patchVersion()))
            .orElse(VersionInfo.UNKNOWN);

    final int decodedRecordVersion = decoder.recordVersion();
    if (decodedRecordVersion == 0
        || decodedRecordVersion == RecordMetadataDecoder.recordVersionNullValue()) {
      recordVersion = DEFAULT_RECORD_VERSION;
    } else {
      recordVersion = decodedRecordVersion;
    }

    // working with variable-length fields
    final int rejectionReasonLength = decoder.rejectionReasonLength();
    if (rejectionReasonLength > 0) {
      decoder.wrapRejectionReason(rejectionReason);
    } else {
      decoder.skipRejectionReason();
    }

    final int authorizationLength = decoder.authorizationLength();
    if (authorizationLength > 0) {
      final DirectBuffer authBuffer = new UnsafeBuffer();
      decoder.wrapAuthorization(authBuffer);
      authorization.wrap(authBuffer);
    } else {
      decoder.skipAuthorization();
    }

    final int agentLength = decoder.agentLength();
    if (agentLength > 0) {
      agent = new AgentInfo();
      final var agentBuffer = new UnsafeBuffer();
      decoder.wrapAgent(agentBuffer);
      agent.wrap(agentBuffer);
    } else {
      decoder.skipAgent();
    }
  }

  @Override
  public int getLength() {
    return BLOCK_LENGTH
        + RecordMetadataEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity()
        + RecordMetadataEncoder.authorizationHeaderLength()
        + authorization.getLength()
        + RecordMetadataEncoder.agentHeaderLength()
        + (agent != null ? agent.getLength() : 0);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);

    // working with fixed-length fields
    encoder
        .recordType(recordType)
        .requestStreamId(requestStreamId)
        .requestId(requestId)
        .protocolVersion(protocolVersion)
        .valueType(valueType)
        .intent(intentValue)
        .rejectionType(rejectionType)
        .recordVersion(recordVersion)
        .operationReference(operationReference)
        .batchOperationReference(batchOperationReference);

    encoder
        .brokerVersion()
        .majorVersion(brokerVersion.getMajorVersion())
        .minorVersion(brokerVersion.getMinorVersion())
        .patchVersion(brokerVersion.getPatchVersion());

    // working with variable-length fields
    encoder.putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
    final var authorizationBuffer = authorization.toDirectBuffer();
    encoder.putAuthorization(authorizationBuffer, 0, authorizationBuffer.capacity());

    if (agent != null) {
      final var bb = agent.toDirectBuffer();
      encoder.putAgent(bb, 0, bb.capacity());
    } else {
      encoder.agent("");
    }
    return headerEncoder.encodedLength() + encoder.encodedLength();
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

  public RecordMetadata authorization(final AuthInfo authorization) {
    this.authorization.copyFrom(authorization);
    return this;
  }

  public RecordMetadata authorization(final DirectBuffer buffer) {
    authorization.wrap(buffer);
    return this;
  }

  public AuthInfo getAuthorization() {
    return authorization;
  }

  public AgentInfo getAgent() {
    return agent;
  }

  public RecordMetadata agent(final AgentInfo agent) {
    this.agent = agent;
    return this;
  }

  public RecordMetadata brokerVersion(final VersionInfo brokerVersion) {
    this.brokerVersion = brokerVersion;
    return this;
  }

  public VersionInfo getBrokerVersion() {
    return brokerVersion;
  }

  public RecordMetadata recordVersion(final int recordVersion) {
    this.recordVersion = recordVersion;
    return this;
  }

  public int getRecordVersion() {
    return recordVersion;
  }

  public RecordMetadata operationReference(final long operationReference) {
    this.operationReference = operationReference;
    return this;
  }

  public long getOperationReference() {
    return operationReference;
  }

  public RecordMetadata batchOperationReference(final long batchOperationReference) {
    this.batchOperationReference = batchOperationReference;
    return this;
  }

  public long getBatchOperationReference() {
    return batchOperationReference;
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
    authorization.reset();
    agent = null;
    brokerVersion = CURRENT_BROKER_VERSION;
    recordVersion = DEFAULT_RECORD_VERSION;
    operationReference = RecordMetadataEncoder.operationReferenceNullValue();
    batchOperationReference = RecordMetadataEncoder.batchOperationReferenceNullValue();
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        requestId,
        valueType,
        recordType,
        intentValue,
        requestStreamId,
        rejectionType,
        rejectionReason,
        authorization,
        protocolVersion,
        brokerVersion,
        recordVersion,
        operationReference,
        batchOperationReference,
        agent);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RecordMetadata that = (RecordMetadata) o;
    return requestId == that.requestId
        && intentValue == that.intentValue
        && requestStreamId == that.requestStreamId
        && protocolVersion == that.protocolVersion
        && valueType == that.valueType
        && recordType == that.recordType
        && rejectionType == that.rejectionType
        && rejectionReason.equals(that.rejectionReason)
        && authorization.equals(that.authorization)
        && Objects.equals(agent, that.agent)
        && brokerVersion.equals(that.brokerVersion)
        && recordVersion == that.recordVersion
        && operationReference == that.operationReference
        && batchOperationReference == that.batchOperationReference;
  }

  @Override
  public String toString() {
    // The toString is intentionally cut-down to the only important properties for debugging
    // (mostly for tests).
    // If the record is already written to the log (in production) we have other ways to make
    // it readable again.
    final var builder =
        new StringBuilder(
            "RecordMetadata{"
                + "recordType="
                + recordType
                + ", valueType="
                + valueType
                + ", intent="
                + intent);
    if (!rejectionType.equals(RejectionType.NULL_VAL)) {
      builder.append(", rejectionType=").append(rejectionType);
    }
    if (rejectionReason.capacity() > 0) {
      builder.append(", rejectionReason=").append(BufferUtil.bufferAsString(rejectionReason));
    }

    if (!authorization.isEmpty()) {
      builder.append(", authorization=").append(authorization);
    }
    if (operationReference != RecordMetadataEncoder.operationReferenceNullValue()) {
      builder.append(", operationReference=").append(operationReference);
    }
    if (batchOperationReference != RecordMetadataEncoder.batchOperationReferenceNullValue()) {
      builder.append(", batchOperationReference=").append(batchOperationReference);
    }
    if (agent != null) {
      builder.append(", agent=").append(agent);
    }

    builder.append('}');
    return builder.toString();
  }
}
