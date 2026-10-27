/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedWaitStateElementTypeFilter;
import io.camunda.gateway.protocol.model.WaitStateElementTypeEnum;
import io.camunda.gateway.protocol.model.WaitStateElementTypeFilterProperty;

public class WaitStateElementTypeFilterPropertyDeserializer
    extends FilterDeserializer<WaitStateElementTypeFilterProperty, WaitStateElementTypeEnum> {

  @Override
  protected Class<? extends WaitStateElementTypeFilterProperty> getFinalType() {
    return AdvancedWaitStateElementTypeFilter.class;
  }

  @Override
  protected Class<WaitStateElementTypeEnum> getImplicitValueType() {
    return WaitStateElementTypeEnum.class;
  }

  @Override
  protected WaitStateElementTypeFilterProperty createFromImplicitValue(
      final WaitStateElementTypeEnum value) {
    return AdvancedWaitStateElementTypeFilter.Builder.create().$eq(value).build();
  }
}
