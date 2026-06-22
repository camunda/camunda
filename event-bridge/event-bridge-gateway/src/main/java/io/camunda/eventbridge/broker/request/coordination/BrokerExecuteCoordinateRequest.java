/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.ExecuteCoordinateResponseDecoder;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.transport.RequestType;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public abstract class BrokerExecuteCoordinateRequest<T> extends BrokerRequest<T> {

  protected final ExecuteCoordinateRequest request = new ExecuteCoordinateRequest();
  protected final ExecuteCoordinateResponse response = new ExecuteCoordinateResponse();

  public BrokerExecuteCoordinateRequest(final CoordinateRequestType type) {
    // The super-constructor takes the schema/template of the RESPONSE this request decodes — not
    // the request's. Previously this passed the request decoder's schema and a hardcoded "2".
    super(ExecuteCoordinateResponseDecoder.SCHEMA_ID, ExecuteCoordinateResponseDecoder.TEMPLATE_ID);
    request.setType(type);
  }

  @Override
  public int getPartitionId() {
    return 1;
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.COORDINATE;
  }

  @Override
  public void setPartitionId(final int partitionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addressesSpecificPartition() {
    return false;
  }

  @Override
  public boolean requiresPartitionId() {
    return false;
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
    final T responseDto = toResponseDto(response.getValue());
    return new BrokerResponse<>(responseDto, -1, -1);
  }

  @Override
  public Optional<BrokerMemberId> getBrokerId() {
    return Optional.of(BrokerMemberId.from(0));
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
