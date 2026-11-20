/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.VariableHistoryItemVariableOperationTypeEnum;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemVariableOperationType;
import org.jspecify.annotations.Nullable;

public final class VariableHistoryItemVariableOperationTypeConverter
    implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof VariableHistoryItemVariableOperationTypeEnum;
  }

  @Override
  public @Nullable String convertValue(final Object value) {
    if (value instanceof final VariableHistoryItemVariableOperationTypeEnum operationTypeEnum) {
      return toInternalVariableOperationTypeAsString(operationTypeEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                VariableHistoryItemVariableOperationTypeEnum.class.getSimpleName()));
  }

  public static @Nullable String toInternalVariableOperationTypeAsString(
      final @Nullable VariableHistoryItemVariableOperationTypeEnum operationTypeEnum) {
    final VariableHistoryItemVariableOperationType internalType =
        toInternalVariableOperationType(operationTypeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static @Nullable VariableHistoryItemVariableOperationType toInternalVariableOperationType(
      final @Nullable VariableHistoryItemVariableOperationTypeEnum operationTypeEnum) {
    return operationTypeEnum == null
        ? null
        : VariableHistoryItemVariableOperationType.valueOf(operationTypeEnum.name());
  }
}
