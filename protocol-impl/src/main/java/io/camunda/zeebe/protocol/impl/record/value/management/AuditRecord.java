/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.management.AuditRecordValue;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class AuditRecord extends UnifiedRecordValue implements AuditRecordValue {
  private static final String EVENTS_KEY = "events";
  private static final String SIZE_KEY = "size";

  private final BinaryProperty events = new BinaryProperty(EVENTS_KEY, new UnsafeBuffer());
  private final IntegerProperty size = new IntegerProperty(SIZE_KEY, 0);

  public AuditRecord() {
    declareProperty(events).declareProperty(size);
  }

  @Override
  public DirectBuffer events() {
    return events.getValue();
  }

  public AuditRecord setEvents(DirectBuffer buffer) {
    events.setValue(buffer);
    return this;
  }

  @JsonIgnore
  public int getSize() {
    return size.getValue();
  }

  public AuditRecord setSize(final int size) {
    this.size.setValue(size);
    return this;
  }
}
