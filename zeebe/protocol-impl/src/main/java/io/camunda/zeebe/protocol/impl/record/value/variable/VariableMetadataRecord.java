/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.EnumValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.protocol.record.value.VariableMetadataValue;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class VariableMetadataRecord extends UnifiedRecordValue
    implements VariableMetadataValue {

  private static final StringValue PROTECTION_MODES_KEY = new StringValue("protectionModes");

  private final ArrayProperty<EnumValue<ProtectionMode>> protectionModesProp =
      new ArrayProperty<>(PROTECTION_MODES_KEY, () -> new EnumValue<>(ProtectionMode.class));

  public VariableMetadataRecord() {
    super(1);
    declareProperty(protectionModesProp);
  }

  @Override
  public Set<ProtectionMode> getProtectionModes() {
    return protectionModesProp.stream()
        .map(EnumValue::getValue)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProtectionMode.class)));
  }

  public VariableMetadataRecord setProtectionModes(
      final Collection<ProtectionMode> protectionModes) {
    protectionModesProp.reset();
    protectionModes.forEach(mode -> protectionModesProp.add().setValue(mode));
    return this;
  }

  public void wrap(final VariableMetadataRecord metadata) {
    setProtectionModes(metadata.getProtectionModes());
  }
}
