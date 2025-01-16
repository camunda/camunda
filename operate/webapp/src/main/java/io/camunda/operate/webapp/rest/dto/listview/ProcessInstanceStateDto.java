/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;

public enum ProcessInstanceStateDto {
  ACTIVE,
  INCIDENT,
  COMPLETED,
  CANCELED,
  UNKNOWN,
  UNSPECIFIED;

  public static ProcessInstanceStateDto getState(final ProcessInstanceState state) {
    if (state == null) {
      return UNSPECIFIED;
    }
    final ProcessInstanceStateDto stateDto = valueOf(state.name());
    if (stateDto == null) {
      return UNKNOWN;
    }
    return stateDto;
  }
}
