/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.DecisionInstanceStateEnum;
import io.camunda.search.entities.DecisionInstanceEntity;
import org.jspecify.annotations.Nullable;

public class DecisionInstanceStateConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return (value instanceof DecisionInstanceStateEnum);
  }

  @Override
  public String convertValue(final Object value) {
    if (value instanceof final DecisionInstanceStateEnum decisionInstanceStateEnum) {
      return DecisionInstanceEntity.DecisionInstanceState.valueOf(decisionInstanceStateEnum.name())
          .name();
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                DecisionInstanceStateEnum.class.getSimpleName()));
  }

  public static @Nullable String toInternalStateAsString(
      final @Nullable DecisionInstanceStateEnum decisionInstanceStateEnum) {
    final DecisionInstanceEntity.DecisionInstanceState internalState =
        toInternalState(decisionInstanceStateEnum);
    return (internalState == null) ? null : internalState.name();
  }

  public static DecisionInstanceEntity.@Nullable DecisionInstanceState toInternalState(
      final @Nullable DecisionInstanceStateEnum decisionInstanceStateEnum) {
    if (decisionInstanceStateEnum == null) {
      return null;
    }
    return DecisionInstanceEntity.DecisionInstanceState.valueOf(decisionInstanceStateEnum.name());
  }
}
