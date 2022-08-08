/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.broker.transport.ApiRequestHandler.RequestReader;
import io.camunda.zeebe.protocol.management.BackupRequestDecoder;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import org.agrona.DirectBuffer;

public final class BackupApiRequestReader implements RequestReader<BackupRequestDecoder> {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final BackupRequestDecoder messageDecoder = new BackupRequestDecoder();

  @Override
  public void reset() {
    // No internal state to reset
  }

  @Override
  public BackupRequestDecoder getMessageDecoder() {
    return messageDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
  }

  public long backupId() {
    return messageDecoder.backupId();
  }

  public long partitionId() {
    return messageDecoder.partitionId();
  }

  public BackupRequestType type() {
    return messageDecoder.type();
  }
}
