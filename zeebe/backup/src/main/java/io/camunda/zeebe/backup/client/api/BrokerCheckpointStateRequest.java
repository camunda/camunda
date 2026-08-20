/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseDecoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BrokerCheckpointStateRequest extends BrokerRequest<CheckpointStateResponse> {

  private final BackupRequest request = new BackupRequest();
  private final CheckpointStateResponse response = new CheckpointStateResponse();

  public BrokerCheckpointStateRequest() {
    super(CheckpointStateResponseDecoder.SCHEMA_ID, CheckpointStateResponseDecoder.TEMPLATE_ID);
    request.setType(BackupRequestType.QUERY_STATE);
  }

  public CheckpointType getCheckpointType() {
    return request.getCheckpointType();
  }

  public void setCheckpointType(final CheckpointType checkpointType) {
    request.setCheckpointType(checkpointType);
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
  protected BrokerResponse<CheckpointStateResponse> readResponse() {
    return new BrokerResponse<>(response);
  }

  @Override
  protected CheckpointStateResponse toResponseDto(final DirectBuffer buffer) {
    return response;
  }

  @Override
  public String getType() {
    return "Backup#state";
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.BACKUP;
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
