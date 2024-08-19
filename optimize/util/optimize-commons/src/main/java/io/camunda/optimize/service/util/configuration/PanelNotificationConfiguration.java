/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class PanelNotificationConfiguration {

  private String url;
  private boolean enabled;
  private String m2mTokenAudience;

  public PanelNotificationConfiguration() {}

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getM2mTokenAudience() {
    return m2mTokenAudience;
  }

  public void setM2mTokenAudience(final String m2mTokenAudience) {
    this.m2mTokenAudience = m2mTokenAudience;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PanelNotificationConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $url = getUrl();
    result = result * PRIME + ($url == null ? 43 : $url.hashCode());
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $m2mTokenAudience = getM2mTokenAudience();
    result = result * PRIME + ($m2mTokenAudience == null ? 43 : $m2mTokenAudience.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PanelNotificationConfiguration)) {
      return false;
    }
    final PanelNotificationConfiguration other = (PanelNotificationConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$url = getUrl();
    final Object other$url = other.getUrl();
    if (this$url == null ? other$url != null : !this$url.equals(other$url)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$m2mTokenAudience = getM2mTokenAudience();
    final Object other$m2mTokenAudience = other.getM2mTokenAudience();
    if (this$m2mTokenAudience == null
        ? other$m2mTokenAudience != null
        : !this$m2mTokenAudience.equals(other$m2mTokenAudience)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PanelNotificationConfiguration(url="
        + getUrl()
        + ", enabled="
        + isEnabled()
        + ", m2mTokenAudience="
        + getM2mTokenAudience()
        + ")";
  }
}
