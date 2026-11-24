/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedVariableHistoryItemActorTypeFilter;
import io.camunda.gateway.protocol.model.VariableHistoryItemActorTypeEnum;
import io.camunda.gateway.protocol.model.VariableHistoryItemActorTypeFilterProperty;

public class VariableHistoryItemActorTypeFilterPropertyDeserializer
    extends FilterDeserializer<
        VariableHistoryItemActorTypeFilterProperty, VariableHistoryItemActorTypeEnum> {

  @Override
  protected Class<? extends VariableHistoryItemActorTypeFilterProperty> getFinalType() {
    return AdvancedVariableHistoryItemActorTypeFilter.class;
  }

  @Override
  protected Class<VariableHistoryItemActorTypeEnum> getImplicitValueType() {
    return VariableHistoryItemActorTypeEnum.class;
  }

  @Override
  protected VariableHistoryItemActorTypeFilterProperty createFromImplicitValue(
      final VariableHistoryItemActorTypeEnum value) {
    return AdvancedVariableHistoryItemActorTypeFilter.Builder.create().$eq(value).build();
  }
}
