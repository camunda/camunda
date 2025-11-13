/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceStateEnum;

public class DecisionInstanceStateConverter
    implements CustomConverter<DecisionInstanceEntity.DecisionInstanceState> {

  @Override
  public boolean canConvert(final Object value) {
    return (value instanceof DecisionInstanceStateEnum);
  }

  @Override
  public DecisionInstanceEntity.DecisionInstanceState convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof DecisionInstanceStateEnum decisionInstanceStateEnum) {
      return toInternalState(decisionInstanceStateEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                DecisionInstanceStateEnum.class.getSimpleName()));
  }

  public static DecisionInstanceEntity.DecisionInstanceState toInternalState(
      DecisionInstanceStateEnum decisionInstanceStateEnum) {
    if (decisionInstanceStateEnum == null) {
      return null;
    }
    return DecisionInstanceEntity.DecisionInstanceState.valueOf(decisionInstanceStateEnum.name());
  }
}
