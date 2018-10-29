package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Period;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ProcessDefinitionCleanupConfiguration {
  @JsonProperty("ttl")
  private Period ttl;
  @JsonProperty("mode")
  private CleanupMode cleanupMode;

  protected ProcessDefinitionCleanupConfiguration() {
  }

  public ProcessDefinitionCleanupConfiguration(Period ttl) {
    this(ttl, null);
  }

  public ProcessDefinitionCleanupConfiguration(CleanupMode cleanupMode) {
    this(null, cleanupMode);
  }

  public ProcessDefinitionCleanupConfiguration(Period ttl, CleanupMode cleanupMode) {
    this.ttl = ttl;
    this.cleanupMode = cleanupMode;
  }

  public Optional<Period> getTtl() {
    return Optional.ofNullable(ttl);
  }

  public Optional<CleanupMode> getCleanupMode() {
    return Optional.ofNullable(cleanupMode);
  }
}
