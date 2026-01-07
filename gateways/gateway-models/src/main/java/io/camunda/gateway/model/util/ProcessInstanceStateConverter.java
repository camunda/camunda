/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.model.util;

import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.search.entities.ProcessInstanceEntity;

public class ProcessInstanceStateConverter implements CustomConverter<String> {

  @Override
  public boolean canConvert(final Object value) {
    return value instanceof ProcessInstanceStateEnum;
  }

  @Override
  public String convertValue(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof final ProcessInstanceStateEnum processInstanceStateEnum) {
      return toInternalStateAsString(processInstanceStateEnum);
    }
    throw new IllegalArgumentException(
        "Cannot convert value [%s] of type [%s]. Expected type: [%s]"
            .formatted(
                value,
                value.getClass().getSimpleName(),
                ProcessInstanceStateEnum.class.getSimpleName()));
  }

  public static String toInternalStateAsString(
      final ProcessInstanceStateEnum processInstanceStateEnum) {
    final ProcessInstanceEntity.ProcessInstanceState internalState =
        toInternalState(processInstanceStateEnum);
    return (internalState == null) ? null : internalState.name();
  }

  public static ProcessInstanceEntity.ProcessInstanceState toInternalState(
      final ProcessInstanceStateEnum processInstanceStateEnum) {
    if (processInstanceStateEnum == null) {
      return null;
    }
    if (processInstanceStateEnum == ProcessInstanceStateEnum.TERMINATED) {
      return ProcessInstanceEntity.ProcessInstanceState.CANCELED;
    }

    return ProcessInstanceEntity.ProcessInstanceState.valueOf(processInstanceStateEnum.name());
  }
}
