/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedFlowNodeInstanceStateFilter;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceStateFilterProperty;

public class FlowNodeInstanceStateFilterPropertyDeserializer
    extends FilterDeserializer<FlowNodeInstanceStateFilterProperty, FlowNodeInstanceStateEnum> {

  @Override
  protected Class<? extends FlowNodeInstanceStateFilterProperty> getFinalType() {
    return AdvancedFlowNodeInstanceStateFilter.class;
  }

  @Override
  protected Class<FlowNodeInstanceStateEnum> getImplicitValueType() {
    return FlowNodeInstanceStateEnum.class;
  }

  @Override
  protected FlowNodeInstanceStateFilterProperty createFromImplicitValue(
      final FlowNodeInstanceStateEnum value) {
    final var filter = new AdvancedFlowNodeInstanceStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
