/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedVariableHistoryItemOperationTypeFilter;
import io.camunda.gateway.protocol.model.VariableHistoryItemOperationTypeEnum;
import io.camunda.gateway.protocol.model.VariableHistoryItemOperationTypeFilterProperty;

public class VariableHistoryItemOperationTypeFilterPropertyDeserializer
    extends FilterDeserializer<
        VariableHistoryItemOperationTypeFilterProperty, VariableHistoryItemOperationTypeEnum> {

  @Override
  protected Class<? extends VariableHistoryItemOperationTypeFilterProperty> getFinalType() {
    return AdvancedVariableHistoryItemOperationTypeFilter.class;
  }

  @Override
  protected Class<VariableHistoryItemOperationTypeEnum> getImplicitValueType() {
    return VariableHistoryItemOperationTypeEnum.class;
  }

  @Override
  protected VariableHistoryItemOperationTypeFilterProperty createFromImplicitValue(
      final VariableHistoryItemOperationTypeEnum value) {
    return AdvancedVariableHistoryItemOperationTypeFilter.Builder.create().$eq(value).build();
  }
}
