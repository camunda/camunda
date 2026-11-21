/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.VariableHistoryItemOperationTypeEnum;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemOperationType;
import org.jspecify.annotations.Nullable;

public final class VariableHistoryItemOperationTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof VariableHistoryItemOperationTypeEnum;
  }

  @Override
  public @Nullable String convertValue(final Object value) {
    if (value instanceof final VariableHistoryItemOperationTypeEnum operationTypeEnum) {
      return toInternalOperationTypeAsString(operationTypeEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                VariableHistoryItemOperationTypeEnum.class.getSimpleName()));
  }

  public static @Nullable String toInternalOperationTypeAsString(
      final @Nullable VariableHistoryItemOperationTypeEnum operationTypeEnum) {
    final VariableHistoryItemOperationType internalType =
        toInternalOperationType(operationTypeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static @Nullable VariableHistoryItemOperationType toInternalOperationType(
      final @Nullable VariableHistoryItemOperationTypeEnum operationTypeEnum) {
    return operationTypeEnum == null
        ? null
        : VariableHistoryItemOperationType.valueOf(operationTypeEnum.name());
  }
}
