/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin;

import io.camunda.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.AdminRequest;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import io.camunda.zeebe.protocol.record.AdminRequestType;
import io.camunda.zeebe.protocol.record.AdminResponseEncoder;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BrokerAdminRequest extends BrokerRequest<Void> {
  private final AdminRequest request = new AdminRequest();
  private final AdminResponse response = new AdminResponse();

  public BrokerAdminRequest() {
    super(AdminResponseEncoder.SCHEMA_ID, AdminResponseEncoder.TEMPLATE_ID);
  }

  public void stepDownIfNotPrimary() {
    request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);
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
    return true;
  }

  @Override
  public boolean requiresPartitionId() {
    return true;
  }

  /** @return null to avoid writing any serialized value */
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
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<Void> readResponse() {
    return new BrokerResponse<>(null);
  }

  @Override
  protected Void toResponseDto(final DirectBuffer buffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getType() {
    return "Admin#" + request.getType();
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.ADMIN;
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    request.write(buffer, offset);
  }
}
