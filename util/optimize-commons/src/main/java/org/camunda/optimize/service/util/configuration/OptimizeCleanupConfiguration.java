package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.HISTORY_CLEANUP;

@JsonIgnoreProperties(ignoreUnknown = false)
public class OptimizeCleanupConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("cronTrigger")
  private String cronTrigger;
  @JsonProperty("ttl")
  private Period defaultTtl;
  @JsonProperty("mode")
  private CleanupMode defaultMode;
  @JsonProperty("perProcessDefinitionConfig")
  private Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration = new HashMap<>();

  public void validate() {
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".cronTrigger must be set and not empty");
    }
    if (defaultTtl == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".ttl must be set");
    }
    if (defaultMode == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".mode must be set");
    }
    if (processDefinitionSpecificConfiguration == null) {
      throw new OptimizeConfigurationException(HISTORY_CLEANUP + ".perProcessDefinitionConfig cannot be null");
    }
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public String getCronTrigger() {
    return cronTrigger;
  }

  public Period getDefaultTtl() {
    return defaultTtl;
  }

  public CleanupMode getDefaultMode() {
    return defaultMode;
  }

  public Map<String, ProcessDefinitionCleanupConfiguration> getProcessDefinitionSpecificConfiguration() {
    return processDefinitionSpecificConfiguration;
  }

  public Optional<ProcessDefinitionCleanupConfiguration> getProcessDefinitionCleanupConfigurationForKey(final String processDefinitionKey) {
    return Optional.ofNullable(processDefinitionSpecificConfiguration)
      .flatMap(map -> Optional.ofNullable(map.get(processDefinitionKey)));
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setCronTrigger(String cronTrigger) {
    this.cronTrigger = cronTrigger;
  }

  public void setDefaultTtl(Period defaultTtl) {
    this.defaultTtl = defaultTtl;
  }

  public void setDefaultMode(CleanupMode defaultMode) {
    this.defaultMode = defaultMode;
  }
}
