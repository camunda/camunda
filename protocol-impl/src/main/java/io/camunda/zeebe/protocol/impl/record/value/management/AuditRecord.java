/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.management.AuditRecordValue;
import org.agrona.DirectBuffer;

public final class AuditRecord extends UnifiedRecordValue implements AuditRecordValue {
  private static final String EVENTS_KEY = "events";

  private final BinaryProperty events = new BinaryProperty(EVENTS_KEY);

  public AuditRecord() {
    declareProperty(events);
  }

  @Override
  public DirectBuffer events() {
    return events.getValue();
  }
}
