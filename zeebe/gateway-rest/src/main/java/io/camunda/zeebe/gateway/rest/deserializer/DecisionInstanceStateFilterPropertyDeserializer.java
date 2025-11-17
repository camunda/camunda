/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedDecisionInstanceStateFilter;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceStateFilterProperty;

public class DecisionInstanceStateFilterPropertyDeserializer
    extends FilterDeserializer<DecisionInstanceStateFilterProperty, DecisionInstanceStateEnum> {

  @Override
  protected Class<? extends DecisionInstanceStateFilterProperty> getFinalType() {
    return AdvancedDecisionInstanceStateFilter.class;
  }

  @Override
  protected Class<DecisionInstanceStateEnum> getImplicitValueType() {
    return DecisionInstanceStateEnum.class;
  }

  @Override
  protected DecisionInstanceStateFilterProperty createFromImplicitValue(
      final DecisionInstanceStateEnum value) {
    final var filter = new AdvancedDecisionInstanceStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
