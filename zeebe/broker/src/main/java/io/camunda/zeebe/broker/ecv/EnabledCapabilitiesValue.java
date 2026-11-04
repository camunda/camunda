/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.ecv.CapabilitySet;

/**
 * The value of the committed ECV in the system partition. 8.11 fixes this format: one property with
 * the encoded capability set, which keeps the capabilities that a binary does not know.
 */
final class EnabledCapabilitiesValue extends UnpackedObject {
  private final StringProperty enabledProp = new StringProperty("enabled", "");

  EnabledCapabilitiesValue() {
    super(1);
    declareProperty(enabledProp);
  }

  CapabilitySet getEnabled() {
    return CapabilitySet.decode(BufferUtil.bufferAsString(enabledProp.getValue()));
  }

  EnabledCapabilitiesValue setEnabled(final CapabilitySet enabled) {
    enabledProp.setValue(enabled.encode());
    return this;
  }
}
