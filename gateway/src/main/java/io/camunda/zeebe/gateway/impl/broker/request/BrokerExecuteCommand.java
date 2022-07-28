/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.gateway.cmd.UnsupportedBrokerResponseException;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejectionResponse;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.transport.RequestType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public abstract class BrokerExecuteCommand<T> extends BrokerRequest<T> {

  protected final ExecuteCommandRequest request = new ExecuteCommandRequest();
  protected final ExecuteCommandResponse response = new ExecuteCommandResponse();
  private final String type;

  public BrokerExecuteCommand(final ValueType valueType, final Intent intent) {
    super(ExecuteCommandResponseDecoder.SCHEMA_ID, ExecuteCommandResponseDecoder.TEMPLATE_ID);
    request.setValueType(valueType);
    request.setIntent(intent);
    type = valueType.name() + "#" + intent.name();
  }

  public long getKey() {
    return request.getKey();
  }

  public Intent getIntent() {
    return request.getIntent();
  }

  public ValueType getValueType() {
    return request.getValueType();
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
    return getPartitionId() != ExecuteCommandRequestEncoder.partitionIdNullValue();
  }

  @Override
  public boolean requiresPartitionId() {
    return true;
  }

  @Override
  protected void setSerializedValue(final DirectBuffer buffer) {
    request.setValue(buffer, 0, buffer.capacity());
  }

  @Override
  protected void wrapResponse(final DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<T> readResponse() {
    if (isRejection()) {
      final BrokerRejection brokerRejection =
          new BrokerRejection(
              request.getIntent(),
              request.getKey(),
              response.getRejectionType(),
              response.getRejectionReason());
      return new BrokerRejectionResponse<>(brokerRejection);
    } else if (isValidResponse()) {
      final T responseDto = toResponseDto(response.getValue());
      return new BrokerResponse<>(responseDto, response.getPartitionId(), response.getKey());
    } else {
      throw new UnsupportedBrokerResponseException(
          request.getValueType().name(), response.getValueType().name());
    }
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.COMMAND;
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    request.write(buffer, offset);
  }

  protected boolean isValidResponse() {
    return response.getValueType() == request.getValueType();
  }

  protected boolean isRejection() {
    return response.getRecordType() == RecordType.COMMAND_REJECTION;
  }
}
