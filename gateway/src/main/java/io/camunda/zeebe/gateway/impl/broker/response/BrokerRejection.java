/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.response;

import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Represents a command rejection from the broker.
 *
 * @param intent the intent of the command that was rejected
 * @param key the key of the command that was rejected
 * @param type the type of the rejection
 * @param reason the reason for the rejection
 */
public record BrokerRejection(Intent intent, long key, RejectionType type, String reason) {
  public BrokerRejection(
      final Intent intent, final long key, final RejectionType type, final DirectBuffer reason) {
    this(intent, key, type, BufferUtil.bufferAsString(reason));
  }
}
