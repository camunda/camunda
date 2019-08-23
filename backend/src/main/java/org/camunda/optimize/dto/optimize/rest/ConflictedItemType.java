/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
