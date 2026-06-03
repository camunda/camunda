/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.ordinal;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.OrdinalRecordValue;

public final class OrdinalRecord extends UnifiedRecordValue implements OrdinalRecordValue {
  // TODO: @yohanfernando >> need to spec this up proper
  private final IntegerProperty ordinalKeyProperty = new IntegerProperty("ordinalKey", 0);

  public OrdinalRecord() {
    super(1);
    declareProperty(ordinalKeyProperty);
  }

  @Override
  public int getOrdinalKey() {
    return ordinalKeyProperty.getValue();
  }

  public OrdinalRecord setOrdinalKey(final int ordinalKey) {
    ordinalKeyProperty.setValue(ordinalKey);
    return this;
  }
}
