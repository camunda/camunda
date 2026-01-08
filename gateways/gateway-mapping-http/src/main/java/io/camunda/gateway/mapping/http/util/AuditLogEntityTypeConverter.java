/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;

public final class AuditLogEntityTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof AuditLogEntityTypeEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final AuditLogEntityTypeEnum entityTypeEnum) {
      return toInternalEntityTypeAsString(entityTypeEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                AuditLogEntityTypeEnum.class.getSimpleName()));
  }

  public static String toInternalEntityTypeAsString(final AuditLogEntityTypeEnum entityTypeEnum) {
    final AuditLogEntityType internalType = toInternalEntityType(entityTypeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static AuditLogEntityType toInternalEntityType(
      final AuditLogEntityTypeEnum entityTypeEnum) {
    if (entityTypeEnum == null) {
      return null;
    }
    return AuditLogEntityType.valueOf(entityTypeEnum.name());
  }
}
