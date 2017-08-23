package org.camunda.optimize.service.util.configuration;

/**
 * @author Askar Akhmerov
 */
public class EngineAuthenticationConfiguration {
  private boolean enabled;
  private String accessGroup;
  private String password;
  private String user;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getAccessGroup() {
    return accessGroup;
  }

  public void setAccessGroup(String accessGroup) {
    this.accessGroup = accessGroup;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }
}
