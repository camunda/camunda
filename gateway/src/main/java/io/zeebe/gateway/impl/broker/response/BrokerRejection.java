/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.response;

import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class BrokerRejection {

  private final Intent intent;
  private final long key;
  private final RejectionType type;
  private final String reason;

  public BrokerRejection(Intent intent, long key, RejectionType type, DirectBuffer reason) {
    this(intent, key, type, BufferUtil.bufferAsString(reason));
  }

  public BrokerRejection(Intent intent, long key, RejectionType type, String reason) {
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
