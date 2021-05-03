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

public final class BrokerRejection {

  private final Intent intent;
  private final long key;
  private final RejectionType type;
  private final String reason;

  public BrokerRejection(
      final Intent intent, final long key, final RejectionType type, final DirectBuffer reason) {
    this(intent, key, type, BufferUtil.bufferAsString(reason));
  }

  public BrokerRejection(
      final Intent intent, final long key, final RejectionType type, final String reason) {
    this.intent = intent;
    this.key = key;
    this.type = type;
    this.reason = reason;
  }

  public RejectionType getType() {
    return type;
  }

  public String getReason() {
    return reason;
  }

  public Intent getIntent() {
    return intent;
  }

  public long getKey() {
    return key;
  }
}
