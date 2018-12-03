package org.camunda.optimize.dto.optimize.rest;

public class SharingEnabledDto {
  private boolean enabled;

  public SharingEnabledDto(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {

    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
