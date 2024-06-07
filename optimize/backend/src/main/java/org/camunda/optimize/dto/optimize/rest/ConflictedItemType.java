/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ConflictedItemType {
  @JsonProperty("alert")
  ALERT,
  @JsonProperty("combined_report")
  COMBINED_REPORT,
  @JsonProperty("dashboard")
  DASHBOARD,
  @JsonProperty("collection")
  COLLECTION,
  @JsonProperty("report")
  REPORT,
  ;
}
