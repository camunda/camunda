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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
