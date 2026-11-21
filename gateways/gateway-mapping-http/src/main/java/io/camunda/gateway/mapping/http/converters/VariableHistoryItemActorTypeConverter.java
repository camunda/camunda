/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.VariableHistoryItemActorTypeEnum;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemActorType;
import org.jspecify.annotations.Nullable;

public final class VariableHistoryItemActorTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof VariableHistoryItemActorTypeEnum;
  }

  @Override
  public @Nullable String convertValue(final Object value) {
    if (value instanceof final VariableHistoryItemActorTypeEnum actorTypeEnum) {
      return toInternalActorTypeAsString(actorTypeEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                VariableHistoryItemActorTypeEnum.class.getSimpleName()));
  }

  public static @Nullable String toInternalActorTypeAsString(
      final @Nullable VariableHistoryItemActorTypeEnum actorTypeEnum) {
    final VariableHistoryItemActorType internalType = toInternalActorType(actorTypeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static @Nullable VariableHistoryItemActorType toInternalActorType(
      final @Nullable VariableHistoryItemActorTypeEnum actorTypeEnum) {
    return actorTypeEnum == null
        ? null
        : VariableHistoryItemActorType.valueOf(actorTypeEnum.name());
  }
}
