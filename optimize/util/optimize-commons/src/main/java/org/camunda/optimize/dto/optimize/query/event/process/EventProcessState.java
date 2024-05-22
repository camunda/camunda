/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EventProcessState {
  UNMAPPED,
  MAPPED,
  PUBLISH_PENDING,
  PUBLISHED,
  UNPUBLISHED_CHANGES,
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
