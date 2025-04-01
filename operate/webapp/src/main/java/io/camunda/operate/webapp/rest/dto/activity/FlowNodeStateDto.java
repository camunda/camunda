/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import io.camunda.webapps.schema.entities.flownode.FlowNodeState;

public enum FlowNodeStateDto {
  ACTIVE,
  INCIDENT,
  COMPLETED,
  TERMINATED,
  UNSPECIFIED,
  UNKNOWN;

  public static FlowNodeStateDto getState(final FlowNodeState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    final FlowNodeStateDto stateDto = valueOf(state.name());
    if (stateDto == null) {
      return UNKNOWN;
    }
    return stateDto;
  }
}
