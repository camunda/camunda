/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clock;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClockControlRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ClockControlRecord extends UnifiedRecordValue implements ClockControlRecordValue {

  private final StringProperty tenantIdProperty =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty timeProperty = new LongProperty("time", -1);

  public ClockControlRecord() {
    super(11);
    declareProperty(tenantIdProperty).declareProperty(timeProperty);
  }

  public void wrap(final ClockControlRecord record) {
    tenantIdProperty.setValue(record.getTenantId());
    timeProperty.setValue(record.getTime());
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProperty.getValue());
  }

  public ClockControlRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }

  @Override
  public long getTime() {
    return timeProperty.getValue();
  }

  public ClockControlRecord setTime(final long time) {
    timeProperty.setValue(time);
    return this;
  }
}
