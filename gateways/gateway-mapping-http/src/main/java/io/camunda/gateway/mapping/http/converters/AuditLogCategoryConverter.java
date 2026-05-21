/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.protocol.model.AuditLogCategoryEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import org.jspecify.annotations.Nullable;

public final class AuditLogCategoryConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof AuditLogCategoryEnum;
  }

  @Override
  public @Nullable String convertValue(final Object value) {
    if (value instanceof final AuditLogCategoryEnum categoryEnum) {
      return toInternalCategoryAsString(categoryEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                AuditLogCategoryEnum.class.getSimpleName()));
  }

  public static @Nullable String toInternalCategoryAsString(
      final @Nullable AuditLogCategoryEnum categoryEnum) {
    final AuditLogOperationCategory internalType = toInternalCategory(categoryEnum);
    return internalType == null ? null : internalType.name();
  }

  public static @Nullable AuditLogOperationCategory toInternalCategory(
      final @Nullable AuditLogCategoryEnum categoryEnum) {
    if (categoryEnum == null) {
      return null;
    }
    return AuditLogOperationCategory.valueOf(categoryEnum.name());
  }
}
