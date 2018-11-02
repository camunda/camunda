package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.time.Period;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

  protected OptimizeCleanupConfiguration() {
  }

  public OptimizeCleanupConfiguration(boolean enabled, String cronTrigger, Period defaultTtl, CleanupMode defaultMode) {
    this.enabled = enabled;
    setCronTrigger(cronTrigger);
    this.defaultTtl = defaultTtl;
    this.defaultMode = defaultMode;
  }

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

  public Set<String> getAllProcessSpecificConfigurationKeys() {
    return new HashSet<>(processDefinitionSpecificConfiguration.keySet());
  }

  public ProcessDefinitionCleanupConfiguration getProcessDefinitionCleanupConfigurationForKey(final String processDefinitionKey) {
    final Optional<ProcessDefinitionCleanupConfiguration> keySpecificConfig =
      Optional.ofNullable(processDefinitionSpecificConfiguration)
        .flatMap(map -> Optional.ofNullable(map.get(processDefinitionKey)));

    return new ProcessDefinitionCleanupConfiguration(
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getTtl())).orElse(getDefaultTtl()),
      keySpecificConfig.flatMap(config -> Optional.ofNullable(config.getCleanupMode())).orElse(getDefaultMode())
    );
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public final void setCronTrigger(String cronTrigger) {
    this.cronTrigger = Optional.ofNullable(cronTrigger).map(this::normalizeToSixParts).orElse(null);
  }

  private String normalizeToSixParts(String cronTrigger) {
    String[] cronParts = cronTrigger.split(" ");
    if (cronParts.length < 6) {
      return cronTrigger + " *";
    } else {
      return cronTrigger;
    }
  }

  public void setDefaultTtl(Period defaultTtl) {
    this.defaultTtl = defaultTtl;
  }

  public void setDefaultMode(CleanupMode defaultMode) {
    this.defaultMode = defaultMode;
  }

  public void setProcessDefinitionSpecificConfiguration(Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration) {
    this.processDefinitionSpecificConfiguration = Optional.ofNullable(processDefinitionSpecificConfiguration).orElse(new HashMap<>());
  }
}
