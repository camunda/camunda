/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

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
  REPORT;
}
