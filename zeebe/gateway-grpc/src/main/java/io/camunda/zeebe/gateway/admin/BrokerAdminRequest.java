/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.AdminRequest;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import io.camunda.zeebe.protocol.management.AdminRequestEncoder;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.protocol.management.AdminResponseEncoder;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BrokerAdminRequest extends BrokerRequest<AdminResponse> {
  private final AdminRequest request = new AdminRequest();
  private final AdminResponse response = new AdminResponse();

  public BrokerAdminRequest() {
    super(AdminResponseEncoder.SCHEMA_ID, AdminResponseEncoder.TEMPLATE_ID);
  }

  public void stepDownIfNotPrimary() {
    request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);
  }

  public void pauseExporting() {
    request.setType(AdminRequestType.PAUSE_EXPORTING);
  }

  public void softPauseExporting() {
    request.setType(AdminRequestType.SOFT_PAUSE_EXPORTING);
  }

  public void resumeExporting() {
    request.setType(AdminRequestType.RESUME_EXPORTING);
  }

  public void getFLowControlConfiguration() {
    request.setType(AdminRequestType.GET_FLOW_CONTROL);
  }

  public void setFlowControlConfiguration(final byte[] configuration) {
    request.setType(AdminRequestType.SET_FLOW_CONTROL);
    request.setPayload(configuration);
  }

  public BrokerAdminRequest banInstance(final long key) {
    request.setType(AdminRequestType.BAN_INSTANCE);
    request.setKey(key);
    request.setPartitionId(Protocol.decodePartitionId(key));
    return this;
  }

  @Override
  public Optional<Integer> getBrokerId() {
    final var brokerId = request.getBrokerId();
    if (brokerId != AdminRequestEncoder.brokerIdNullValue()) {
      return Optional.of(brokerId);
    } else {
      return Optional.empty();
    }
  }

  public void setBrokerId(final int brokerId) {
    request.setBrokerId(brokerId);
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

  /**
   * @return null to avoid writing any serialized value
   */
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
  protected BrokerResponse<AdminResponse> readResponse() {
    return new BrokerResponse<>(response);
  }

  @Override
  protected AdminResponse toResponseDto(final DirectBuffer buffer) {
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
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return request.write(buffer, offset);
  }
}
