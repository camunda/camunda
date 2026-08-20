/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import io.camunda.zeebe.broker.client.api.UnsupportedBrokerResponseException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** Wraps the request and response for "Take Backup" sent between gateway and broker */
public final class BrokerBackupRequest extends BrokerRequest<BackupResponse> {

  final BackupRequest request = new BackupRequest();
  final ExecuteCommandResponse response = new ExecuteCommandResponse();

  public BrokerBackupRequest() {
    super(ExecuteCommandResponseDecoder.SCHEMA_ID, ExecuteCommandResponseDecoder.TEMPLATE_ID);
    request.setType(BackupRequestType.TAKE_BACKUP);
  }

  @Override
  public int getPartitionId() {
    return request.getPartitionId();
  }

  @Override
  public void setPartitionId(final int partitionId) {
    request.setPartitionId(partitionId);
  }

  @Override
  public boolean addressesSpecificPartition() {
    return true;
  }

  @Override
  public boolean requiresPartitionId() {
    return true;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return null;
  }

  @Override
  protected void setSerializedValue(final DirectBuffer buffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void wrapResponse(final DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<BackupResponse> readResponse() {
    if (response.getRecordType() == RecordType.COMMAND_REJECTION) {
      final BrokerRejection brokerRejection =
          new BrokerRejection(
              CheckpointIntent.CREATE,
              request.getBackupId(),
              response.getRejectionType(),
              response.getRejectionReason());
      return new BrokerRejectionResponse<>(brokerRejection);
    } else if (response.getValueType() == ValueType.CHECKPOINT) {
      final var responseDto = toResponseDto(response.getValue());
      return new BrokerResponse<>(responseDto, response.getPartitionId(), response.getKey());
    } else {
      throw new UnsupportedBrokerResponseException(
          ValueType.CHECKPOINT.name(), response.getValueType().name());
    }
  }

  @Override
  protected BackupResponse toResponseDto(final DirectBuffer buffer) {
    final CheckpointRecord responseDto = new CheckpointRecord();
    responseDto.wrap(buffer);

    return new BackupResponse(
        response.getIntent() == CheckpointIntent.CREATED, responseDto.getCheckpointId());
  }

  @Override
  public String getType() {
    return "Backup#take";
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.BACKUP;
  }

  public long getBackupId() {
    return request.getBackupId();
  }

  public void setBackupId(final long backupId) {
    request.setBackupId(backupId);
  }

  public String getPattern() {
    return request.getPattern();
  }

  public void setPattern(final String pattern) {
    request.setPattern(pattern);
  }

  public CheckpointType getCheckpointType() {
    return request.getCheckpointType();
  }

  public void setCheckpointType(final CheckpointType checkpointType) {
    request.setCheckpointType(checkpointType);
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return request.write(buffer, offset);
  }
}
