package org.camunda.optimize.service.util.configuration;

/**
 * @author Askar Akhmerov
 */
public class EngineConfiguration {

  private String name;
  private String rest;
  private String alias;
  private boolean enabled;

  private EngineAuthenticationConfiguration authentication;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRest() {
    return rest;
  }

  public void setRest(String rest) {
    rest = removeTrailingSlashes(rest);
    this.rest = rest;
  }

  private String removeTrailingSlashes(String str) {
    return str.replaceAll("/$", "");
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public EngineAuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public void setAuthentication(EngineAuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }
}
