/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;

public final class AuditLogActorTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof AuditLogActorTypeEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final AuditLogActorTypeEnum categoryEnum) {
      return toInternalActorTypeAsString(categoryEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                AuditLogActorTypeEnum.class.getSimpleName()));
  }

  public static String toInternalActorTypeAsString(final AuditLogActorTypeEnum categoryEnum) {
    final AuditLogActorType internalType = toInternalActorType(categoryEnum);
    return internalType == null ? null : internalType.name();
  }

  public static AuditLogActorType toInternalActorType(final AuditLogActorTypeEnum categoryEnum) {
    if (categoryEnum == null) {
      return null;
    }
    return AuditLogActorType.valueOf(categoryEnum.name());
  }
}
