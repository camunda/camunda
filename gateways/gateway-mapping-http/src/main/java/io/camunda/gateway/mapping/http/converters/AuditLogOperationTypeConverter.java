/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import org.jspecify.annotations.Nullable;

public final class AuditLogOperationTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof AuditLogOperationTypeEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value instanceof final AuditLogOperationTypeEnum operationTypeEnum) {
      return AuditLogOperationType.valueOf(operationTypeEnum.name()).name();
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                AuditLogOperationTypeEnum.class.getSimpleName()));
  }

  public static @Nullable String toInternalOperationTypeAsString(
      final @Nullable AuditLogOperationTypeEnum operationTypeEnum) {
    final AuditLogOperationType internalType = toInternalOperationType(operationTypeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static @Nullable AuditLogOperationType toInternalOperationType(
      final @Nullable AuditLogOperationTypeEnum operationTypeEnum) {
    if (operationTypeEnum == null) {
      return null;
    }
    return AuditLogOperationType.valueOf(operationTypeEnum.name());
  }
}
