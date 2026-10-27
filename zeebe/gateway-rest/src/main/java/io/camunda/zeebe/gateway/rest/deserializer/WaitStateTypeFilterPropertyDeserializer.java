/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedWaitStateTypeFilter;
import io.camunda.gateway.protocol.model.WaitStateTypeEnum;
import io.camunda.gateway.protocol.model.WaitStateTypeFilterProperty;

public class WaitStateTypeFilterPropertyDeserializer
    extends FilterDeserializer<WaitStateTypeFilterProperty, WaitStateTypeEnum> {

  @Override
  protected Class<? extends WaitStateTypeFilterProperty> getFinalType() {
    return AdvancedWaitStateTypeFilter.class;
  }

  @Override
  protected Class<WaitStateTypeEnum> getImplicitValueType() {
    return WaitStateTypeEnum.class;
  }

  @Override
  protected WaitStateTypeFilterProperty createFromImplicitValue(final WaitStateTypeEnum value) {
    return AdvancedWaitStateTypeFilter.Builder.create().$eq(value).build();
  }
}
