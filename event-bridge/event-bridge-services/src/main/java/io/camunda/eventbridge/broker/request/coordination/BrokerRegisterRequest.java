/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.RegisterBrokerRequest;
import io.camunda.eventbridge.protocol.request.coordination.RegisterBrokerResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Broker-client request for a broker to (re-)register with the metadata-group leader. Routed to the
 * metadata routing group's leader (partition 1, the topic-registry shard); the leader's {@code
 * RegisterBrokerProcessor} assigns the broker epoch returned in the {@link RegisterBrokerResponse}.
 */
public class BrokerRegisterRequest extends BrokerExecuteCoordinateRequest<RegisterBrokerResponse> {

  private final RegisterBrokerRequest request = new RegisterBrokerRequest();
  private final RegisterBrokerResponse response = new RegisterBrokerResponse();

  public BrokerRegisterRequest() {
    super(CoordinateRequestType.REGISTER_BROKER, METADATA_ROUTING_GROUP);
  }

  public BrokerRegisterRequest wrapRequest(final int brokerId, final long incarnation) {
    request.setBrokerId(brokerId).setIncarnation(incarnation);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected RegisterBrokerResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "RegisterBroker";
  }
}
