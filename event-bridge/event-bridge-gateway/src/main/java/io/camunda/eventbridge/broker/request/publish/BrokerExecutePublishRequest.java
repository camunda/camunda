/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.publish;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.eventbridge.protocol.ExecutePublishResponseDecoder;
import io.camunda.eventbridge.protocol.request.PublishBatchResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.transport.RequestType;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class BrokerExecutePublishRequest<T> extends BrokerRequest<T> {

  private final ExecutePublishRequest request = new ExecutePublishRequest();
  private final ExecutePublishResponse response = new ExecutePublishResponse();
  private int partitionId = 1;

  public BrokerExecutePublishRequest() {
    super(ExecutePublishResponseDecoder.SCHEMA_ID, ExecutePublishResponseDecoder.TEMPLATE_ID);
  }

  public void wrapPublishResponse(final PublishBatchResponse target) {
    target.wrapPublishResponse(response.getResponse());
  }

  public BrokerExecutePublishRequest<T> wrapBatch(final byte[] batch) {
    request.wrapValue(new UnsafeBuffer(batch), 0, batch.length);
    return this;
  }

  public BrokerExecutePublishRequest<T> partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.PUBLISH;
  }

  @Override
  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
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
  protected void setSerializedValue(final DirectBuffer buffer) {
    request.wrapValue(buffer, 0, buffer.capacity());
  }

  @Override
  protected void wrapResponse(final DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<T> readResponse() {
    final T responseDto = toResponseDto(null);
    return new BrokerResponse<>(responseDto, -1, -1);
  }

  @Override
  public Optional<BrokerMemberId> getBrokerId() {
    // Empty: the BrokerClient resolves the partition leader from cluster topology
    // (addressesSpecificPartition → getLeaderForPartition) and retries on NOT_LEADER.
    return Optional.empty();
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
