/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.converters;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationTypeEnum;
import io.camunda.search.entities.BatchOperationType;

public final class BatchOperationTypeConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof GeneratedBatchOperationTypeEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final GeneratedBatchOperationTypeEnum categoryEnum) {
      return toInternalBatchOperationTypeAsString(categoryEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                GeneratedBatchOperationTypeEnum.class.getSimpleName()));
  }

  public static String toInternalBatchOperationTypeAsString(
      final GeneratedBatchOperationTypeEnum typeEnum) {
    final BatchOperationType internalType = toInternalBatchOperationType(typeEnum);
    return internalType == null ? null : internalType.name();
  }

  public static BatchOperationType toInternalBatchOperationType(
      final GeneratedBatchOperationTypeEnum categoryEnum) {
    if (categoryEnum == null) {
      return null;
    }
    return BatchOperationType.valueOf(categoryEnum.name());
  }
}
