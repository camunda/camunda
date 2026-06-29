/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.BrokerHeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.BrokerHeartbeatResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Broker-client request for a broker's periodic liveness heartbeat to the metadata-group leader
 * (partition 1, the topic-registry shard). Distinct from {@code BrokerHeartbeatRequest}, which is
 * the consumer-group heartbeat — this one carries the broker's node id + epoch and the {@code
 * draining} flag for controlled shutdown.
 */
public class BrokerLivenessHeartbeatRequest
    extends BrokerExecuteCoordinateRequest<BrokerHeartbeatResponse> {

  private final BrokerHeartbeatRequest request = new BrokerHeartbeatRequest();
  private final BrokerHeartbeatResponse response = new BrokerHeartbeatResponse();

  public BrokerLivenessHeartbeatRequest() {
    super(CoordinateRequestType.BROKER_HEARTBEAT, METADATA_ROUTING_GROUP);
  }

  public BrokerLivenessHeartbeatRequest wrapRequest(
      final int brokerId, final long brokerEpoch, final boolean draining) {
    request.setBrokerId(brokerId).setBrokerEpoch(brokerEpoch).setDraining(draining);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected BrokerHeartbeatResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "BrokerHeartbeat";
  }
}
