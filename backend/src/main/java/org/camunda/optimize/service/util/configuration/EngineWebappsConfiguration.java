package org.camunda.optimize.service.util.configuration;

public class EngineWebappsConfiguration {

  private String endpoint;
  private boolean enabled;

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
