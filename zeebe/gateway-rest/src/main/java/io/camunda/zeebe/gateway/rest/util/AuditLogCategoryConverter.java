/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogCategoryEnum;

public final class AuditLogCategoryConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof AuditLogCategoryEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
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

  public static String toInternalCategoryAsString(final AuditLogCategoryEnum categoryEnum) {
    final AuditLogOperationCategory internalType = toInternalCategory(categoryEnum);
    return internalType == null ? null : internalType.name();
  }

  public static AuditLogOperationCategory toInternalCategory(
      final AuditLogCategoryEnum categoryEnum) {
    if (categoryEnum == null) {
      return null;
    }
    return AuditLogOperationCategory.valueOf(categoryEnum.name());
  }
}
