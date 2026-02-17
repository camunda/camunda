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
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.GetSnapshotChunk;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.DeleteSnapshotForBootstrapResponseEncoder;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.SnapshotChunkResponseEncoder;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.SnapshotRequestSerializer;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.SnapshotResponseDeserializer;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class SnapshotBrokerRequest extends BrokerRequest<SnapshotResponse> {

  final SnapshotRequestSerializer serializer = new SnapshotRequestSerializer();
  final SnapshotResponseDeserializer responseDeserializer = new SnapshotResponseDeserializer();
  ExecuteCommandResponse response = new ExecuteCommandResponse();
  SnapshotResponse snapshotResponse;
  private SnapshotRequest request;

  public SnapshotBrokerRequest(final SnapshotRequest request) {
    super(SnapshotChunkResponseEncoder.SCHEMA_ID, templateId(request));
    this.request = request;
  }

  private static int templateId(final SnapshotRequest request) {
    return switch (request) {
      case final DeleteSnapshotForBootstrapRequest r ->
          DeleteSnapshotForBootstrapResponseEncoder.TEMPLATE_ID;
      case final GetSnapshotChunk r -> SnapshotChunkResponseEncoder.TEMPLATE_ID;
    };
  }

  public SnapshotRequest getRequest() {
    return request;
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
    return request.addressesSpecificPartition();
  }

  @Override
  public boolean requiresPartitionId() {
    return request.requiresPartitionId();
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
    snapshotResponse = responseDeserializer.deserialize(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<SnapshotResponse> readResponse() {
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
  protected SnapshotResponse toResponseDto(final DirectBuffer buffer) {
    return snapshotResponse;
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
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return serializer.serialize(request, buffer, offset);
  }
}
