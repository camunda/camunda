/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.AuditLogResultEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;

public final class AuditLogResultConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof AuditLogResultEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final AuditLogResultEnum resultEnum) {
      return toInternalResultAsString(resultEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value, value.getClass().getSimpleName(), AuditLogResultEnum.class.getSimpleName()));
  }

  public static String toInternalResultAsString(final AuditLogResultEnum resultEnum) {
    final AuditLogOperationResult internalType = toInternalResult(resultEnum);
    return internalType == null ? null : internalType.name();
  }

  public static AuditLogOperationResult toInternalResult(final AuditLogResultEnum resultEnum) {
    if (resultEnum == null) {
      return null;
    }
    return AuditLogOperationResult.valueOf(resultEnum.name());
  }
}
