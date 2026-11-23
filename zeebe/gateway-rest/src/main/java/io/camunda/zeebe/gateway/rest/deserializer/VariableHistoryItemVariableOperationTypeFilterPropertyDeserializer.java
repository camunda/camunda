/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedVariableHistoryItemVariableOperationTypeFilter;
import io.camunda.gateway.protocol.model.VariableHistoryItemVariableOperationTypeEnum;
import io.camunda.gateway.protocol.model.VariableHistoryItemVariableOperationTypeFilterProperty;

public class VariableHistoryItemVariableOperationTypeFilterPropertyDeserializer
    extends FilterDeserializer<
        VariableHistoryItemVariableOperationTypeFilterProperty,
        VariableHistoryItemVariableOperationTypeEnum> {

  @Override
  protected Class<? extends VariableHistoryItemVariableOperationTypeFilterProperty> getFinalType() {
    return AdvancedVariableHistoryItemVariableOperationTypeFilter.class;
  }

  @Override
  protected Class<VariableHistoryItemVariableOperationTypeEnum> getImplicitValueType() {
    return VariableHistoryItemVariableOperationTypeEnum.class;
  }

  @Override
  protected VariableHistoryItemVariableOperationTypeFilterProperty createFromImplicitValue(
      final VariableHistoryItemVariableOperationTypeEnum value) {
    return AdvancedVariableHistoryItemVariableOperationTypeFilter.Builder.create()
        .$eq(value)
        .build();
  }
}
