/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AuthorizationType {
  // Users with this authorization are permitted to configure telemetry settings
  TELEMETRY("telemetry_administration"),
  // Users with this authorization are permitted to export data as CSV
  CSV_EXPORT("csv_export"),
  // Users with this authorization are permitted to create/edit/delete entities outside a collection
  ENTITY_EDITOR("entity_editor");

  private final String id;

  AuthorizationType(final String id) {
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
