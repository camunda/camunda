/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.GetSnapshotChunk;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotChunkResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.GetSnapshotChunkSerializer;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.SnapshotChunkResponseDeserializer;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.SnapshotChunkResponseEncoder;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class GetSnapshotChunkBrokerRequest extends BrokerRequest<SnapshotChunkResponse> {

  final GetSnapshotChunkSerializer serializer = new GetSnapshotChunkSerializer();
  final SnapshotChunkResponseDeserializer responseDeserializer =
      new SnapshotChunkResponseDeserializer();
  ExecuteCommandResponse response = new ExecuteCommandResponse();
  SnapshotChunkResponse chunkResponse;
  private GetSnapshotChunk request;

  public GetSnapshotChunkBrokerRequest(final GetSnapshotChunk request) {
    super(SnapshotChunkResponseEncoder.SCHEMA_ID, SnapshotChunkResponseEncoder.TEMPLATE_ID);
    this.request = request;
  }

  public void setRequest(final GetSnapshotChunk request) {
    this.request = request;
  }

  @Override
  public int getPartitionId() {
    return request.partitionId();
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.SNAPSHOT;
  }

  @Override
  public void setPartitionId(final int partitionId) {
    request = request.withPartitionId(partitionId);
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
    chunkResponse = responseDeserializer.deserialize(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<SnapshotChunkResponse> readResponse() {
    if (response.getRecordType() == RecordType.COMMAND_REJECTION) {
      final BrokerRejection brokerRejection =
          new BrokerRejection(null, -1, response.getRejectionType(), response.getRejectionReason());
      return new BrokerRejectionResponse<>(brokerRejection);
    } else {
      final var responseDto = toResponseDto(response.getValue());
      return new BrokerResponse<>(responseDto, response.getPartitionId(), response.getKey());
    }
  }

  @Override
  protected SnapshotChunkResponse toResponseDto(final DirectBuffer buffer) {
    return chunkResponse;
  }

  @Override
  public String getType() {
    return "Snapshot#getChunk";
  }

  @Override
  public int getLength() {
    return serializer.size(request);
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    serializer.serialize(request, buffer, offset);
  }
}
