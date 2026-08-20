/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.protocol.management.BackupRequestDecoder;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import org.agrona.DirectBuffer;

public final class BackupApiRequestReader implements RequestReader {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final BackupRequestDecoder messageDecoder = new BackupRequestDecoder();

  @Override
  public void reset() {
    // No internal state to reset
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
  }

  public long backupId() {
    return messageDecoder.backupId();
  }

  public String pattern() {
    return messageDecoder.pattern();
  }

  public int partitionId() {
    return messageDecoder.partitionId();
  }

  public BackupRequestType type() {
    return messageDecoder.type();
  }

  public CheckpointType checkpointType() {
    if (messageDecoder.checkpointType() != BackupRequestDecoder.checkpointTypeNullValue()) {
      return CheckpointType.valueOf(messageDecoder.checkpointType());
    }
    return CheckpointType.MANUAL_BACKUP;
  }
}
