/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EntityType {
  COLLECTION,
  DASHBOARD,
  REPORT,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }

  public static EntityType valueOfId(final String id) {
    return valueOf(id.toUpperCase());
  }

  @Override
  public String toString() {
    return getId();
  }
}
