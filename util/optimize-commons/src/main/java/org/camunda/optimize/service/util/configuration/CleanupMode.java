package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CleanupMode {
  @JsonProperty("all")
  ALL,
  @JsonProperty("variables")
  VARIABLES,
  ;
}
