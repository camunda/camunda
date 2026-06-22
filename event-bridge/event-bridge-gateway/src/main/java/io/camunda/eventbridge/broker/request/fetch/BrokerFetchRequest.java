/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.fetch;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.eventbridge.protocol.FetchResponseDecoder;
import io.camunda.eventbridge.protocol.request.FetchRequest;
import io.camunda.eventbridge.protocol.request.FetchResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Fetch request routed to the leader of {@code partitionId}. The response is an SBE {@code
 * FetchResponse} whose {@code data} the broker streams zero-copy; the gateway decodes it like any
 * other SBE response.
 */
public class BrokerFetchRequest extends BrokerRequest<FetchResponse> {

  private final FetchRequest request = new FetchRequest();
  private final FetchResponse response = new FetchResponse();
  private int partitionId;

  public BrokerFetchRequest() {
    super(FetchResponseDecoder.SCHEMA_ID, FetchResponseDecoder.TEMPLATE_ID);
  }

  public BrokerFetchRequest setup(
      final int partitionId,
      final long fromPosition,
      final int maxBytes,
      final int minBytes,
      final long maxWaitMs) {
    this.partitionId = partitionId;
    request
        .partitionId(partitionId)
        .fromPosition(fromPosition)
        .maxBytes(maxBytes)
        .minBytes(minBytes)
        .maxWaitMs(maxWaitMs);
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    request.partitionId(partitionId);
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.FETCH;
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
  public Optional<BrokerMemberId> getBrokerId() {
    // Empty: the BrokerClient resolves the partition leader from cluster topology.
    return Optional.empty();
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected void setSerializedValue(final DirectBuffer buffer) {
    request.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected void wrapResponse(final DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<FetchResponse> readResponse() {
    return new BrokerResponse<>(response, -1, -1);
  }

  @Override
  protected FetchResponse toResponseDto(final DirectBuffer buffer) {
    return response;
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return request.write(buffer, offset);
  }

  @Override
  public String getType() {
    return "Fetch";
  }
}
