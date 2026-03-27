/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogCategoryEnum;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;

public final class AuditLogCategoryConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof GeneratedAuditLogCategoryEnum || value instanceof String;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final GeneratedAuditLogCategoryEnum categoryEnum) {
      return toInternalCategoryAsString(categoryEnum);
    }
    if (value instanceof final String stringValue) {
      return toInternalCategoryAsString(GeneratedAuditLogCategoryEnum.fromValue(stringValue));
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                GeneratedAuditLogCategoryEnum.class.getSimpleName()));
  }

  public static String toInternalCategoryAsString(
      final GeneratedAuditLogCategoryEnum categoryEnum) {
    final AuditLogOperationCategory internalType = toInternalCategory(categoryEnum);
    return internalType == null ? null : internalType.name();
  }

  public static AuditLogOperationCategory toInternalCategory(
      final GeneratedAuditLogCategoryEnum categoryEnum) {
    if (categoryEnum == null) {
      return null;
    }
    return AuditLogOperationCategory.valueOf(categoryEnum.name());
  }
}
