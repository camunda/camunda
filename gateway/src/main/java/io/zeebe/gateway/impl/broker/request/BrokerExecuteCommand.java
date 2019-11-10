/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.request;

import io.opentracing.Tracer;
import io.opentracing.propagation.Format.Builtin;
import io.zeebe.gateway.cmd.UnsupportedBrokerResponseException;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerRejectionResponse;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public abstract class BrokerExecuteCommand<T> extends BrokerRequest<T> {

  protected final ExecuteCommandRequest request = new ExecuteCommandRequest();
  protected final ExecuteCommandResponse response = new ExecuteCommandResponse();

  public BrokerExecuteCommand(final ValueType valueType, final Intent intent) {
    super(ExecuteCommandResponseDecoder.SCHEMA_ID, ExecuteCommandResponseDecoder.TEMPLATE_ID);
    request.setValueType(valueType);
    request.setIntent(intent);
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
  public void injectTrace(final Tracer tracer) {
    tracer.inject(activeSpan.context(), Builtin.BINARY, request.getSpanContextAdapter());
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
