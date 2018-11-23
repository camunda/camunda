package org.camunda.optimize.dto.optimize.query.report.single.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SortOrder {
  @JsonProperty("asc")
  ASC,
  @JsonProperty("desc")
  DESC,
  ;
}
