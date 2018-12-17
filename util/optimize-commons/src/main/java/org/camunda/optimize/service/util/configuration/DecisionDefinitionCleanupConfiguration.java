package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Period;

@JsonIgnoreProperties(ignoreUnknown = false)
public class DecisionDefinitionCleanupConfiguration {
  @JsonProperty("ttl")
  private Period ttl;

  protected DecisionDefinitionCleanupConfiguration() {
  }

  public DecisionDefinitionCleanupConfiguration(Period ttl) {
    this.ttl = ttl;

  }

  public Period getTtl() {
    return ttl;
  }

}
