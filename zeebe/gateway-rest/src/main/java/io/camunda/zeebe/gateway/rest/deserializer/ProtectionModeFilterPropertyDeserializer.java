/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedProtectionModeFilter;
import io.camunda.gateway.protocol.model.ProtectionModeEnum;
import io.camunda.gateway.protocol.model.ProtectionModeFilterProperty;
import java.util.List;

public class ProtectionModeFilterPropertyDeserializer
    extends FilterDeserializer<ProtectionModeFilterProperty, ProtectionModeEnum> {

  @Override
  protected Class<? extends ProtectionModeFilterProperty> getFinalType() {
    return AdvancedProtectionModeFilter.class;
  }

  @Override
  protected Class<ProtectionModeEnum> getImplicitValueType() {
    return ProtectionModeEnum.class;
  }

  @Override
  protected ProtectionModeFilterProperty createFromImplicitValue(final ProtectionModeEnum value) {
    return AdvancedProtectionModeFilter.Builder.create().$in(List.of(value)).build();
  }
}
