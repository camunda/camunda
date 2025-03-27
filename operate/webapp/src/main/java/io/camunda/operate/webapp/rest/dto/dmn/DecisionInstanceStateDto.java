/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;

public enum DecisionInstanceStateDto {
  FAILED,
  EVALUATED,
  UNKNOWN,
  UNSPECIFIED;

  public static DecisionInstanceStateDto getState(final DecisionInstanceState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    final DecisionInstanceStateDto stateDto = valueOf(state.name());
    if (stateDto == null) {
      return UNKNOWN;
    }
    return stateDto;
  }
}
