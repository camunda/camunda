/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EventScopeType {
  ALL,
  START_END,
  PROCESS_INSTANCE,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }

  @Override
  public String toString() {
    return getId();
  }
}