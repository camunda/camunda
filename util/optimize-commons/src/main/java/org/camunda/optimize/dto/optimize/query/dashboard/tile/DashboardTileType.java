/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard.tile;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DashboardTileType {

  OPTIMIZE_REPORT("optimize_report"),
  EXTERNAL_URL("external_url"),
  TEXT("text");

  private final String id;

  DashboardTileType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

}
