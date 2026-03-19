/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogEntityTypeEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;

public final class AuditLogEntityTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof GeneratedAuditLogEntityTypeEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final GeneratedAuditLogEntityTypeEnum entityTypeEnum) {
      return toInternalEntityTypeAsString(entityTypeEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                GeneratedAuditLogEntityTypeEnum.class.getSimpleName()));
  }

  public static String toInternalEntityTypeAsString(
      final GeneratedAuditLogEntityTypeEnum entityTypeEnum) {
    final AuditLogEntityType internalType = toInternalEntityType(entityTypeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static AuditLogEntityType toInternalEntityType(
      final GeneratedAuditLogEntityTypeEnum entityTypeEnum) {
    if (entityTypeEnum == null) {
      return null;
    }
    return AuditLogEntityType.valueOf(entityTypeEnum.name());
  }
}
