/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.capabilities;

import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterCapabilitiesRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ClusterCapabilitiesRecord extends UnifiedRecordValue
    implements ClusterCapabilitiesRecordValue {

  private final StringProperty enabledCapabilitiesProperty =
      new StringProperty("enabledCapabilities", "");

  public ClusterCapabilitiesRecord() {
    super(1);
    declareProperty(enabledCapabilitiesProperty);
  }

  @Override
  public String getEnabledCapabilities() {
    return BufferUtil.bufferAsString(enabledCapabilitiesProperty.getValue());
  }

  public ClusterCapabilitiesRecord setEnabledCapabilities(final String enabledCapabilities) {
    enabledCapabilitiesProperty.setValue(enabledCapabilities);
    return this;
  }
}
