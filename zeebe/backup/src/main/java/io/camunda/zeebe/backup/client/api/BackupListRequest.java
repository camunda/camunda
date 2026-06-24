/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.management.BackupListResponseDecoder;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BackupListRequest extends BrokerRequest<BackupListResponse> {

  protected final BackupRequest request = new BackupRequest();
  protected final BackupListResponse response = new BackupListResponse();

  public BackupListRequest() {
    super(BackupListResponseDecoder.SCHEMA_ID, BackupListResponseDecoder.TEMPLATE_ID);
    request.setType(BackupRequestType.LIST);
  }

  public long getBackupId() {
    return request.getBackupId();
  }

  public void setBackupId(final long backupId) {
    request.setBackupId(backupId);
  }

  public String getPattern() {
    return request.getPattern();
  }

  public void setPattern(final String pattern) {
    request.setPattern(pattern);
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
  protected BrokerResponse<BackupListResponse> readResponse() {
    return new BrokerResponse<>(response, getPartitionId(), -1);
  }

  @Override
  protected BackupListResponse toResponseDto(final DirectBuffer buffer) {
    return response;
  }

  @Override
  public String getType() {
    return "Backup#list";
  }

  @Override
  public RequestType getRequestType() {
    return RequestType.BACKUP;
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
