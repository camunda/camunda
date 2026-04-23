/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.publish;

import io.camunda.eventbridge.protocol.ExecutePublishResponseDecoder;
import io.camunda.eventbridge.protocol.ExecutePublishResponseEncoder;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BrokerFetchRequest extends BrokerRequest<Void> {

  public BrokerFetchRequest() {
    super(ExecutePublishResponseDecoder.SCHEMA_ID, ExecutePublishResponseEncoder.TEMPLATE_ID);
  }

  @Override
  public int getPartitionId() {
    return 1;
  }

  @Override
  public void setPartitionId(final int partitionId) {}

  @Override
  public boolean addressesSpecificPartition() {
    return false;
  }

  @Override
  public boolean requiresPartitionId() {
    return false;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return null;
  }

  @Override
  protected void setSerializedValue(final DirectBuffer buffer) {}

  @Override
  protected void wrapResponse(final DirectBuffer buffer) {}

  @Override
  protected BrokerResponse<Void> readResponse() {
    return null;
  }

  @Override
  protected Void toResponseDto(final DirectBuffer buffer) {
    return null;
  }

  @Override
  public String getType() {
    return "";
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.FETCH;
  }

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return 0;
  }
}
