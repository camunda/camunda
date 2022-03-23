/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExportEntityType {
  SINGLE_PROCESS_REPORT,
  SINGLE_DECISION_REPORT,
  COMBINED_REPORT,
  DASHBOARD;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }

  @Override
  public String toString() {
    return getId();
  }

  // This is used by jersey on unmarshalling query/path parameters
  // see https://docs.jboss.org/resteasy/docs/3.5.0.Final/userguide/html/StringConverter.html#d4e1541
  public static ExportEntityType fromString(final String name) {
    return valueOf(name.toUpperCase());
  }
}
