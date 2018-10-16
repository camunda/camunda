package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ConflictedItemType {
  @JsonProperty("alert")
  ALERT,
  @JsonProperty("combined_report")
  COMBINED_REPORT,
  @JsonProperty("dashboard")
  DASHBOARD,
  ;
}
