/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

import java.util.Objects;

public class AnalyticsConfigResponseDto {

  private boolean enabled;
  private String osanoScriptUrl;

  public AnalyticsConfigResponseDto(final boolean enabled, final String osanoScriptUrl) {
    this.enabled = enabled;
    this.osanoScriptUrl = osanoScriptUrl;
  }

  public AnalyticsConfigResponseDto() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getOsanoScriptUrl() {
    return osanoScriptUrl;
  }

  public void setOsanoScriptUrl(final String osanoScriptUrl) {
    this.osanoScriptUrl = osanoScriptUrl;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, osanoScriptUrl);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AnalyticsConfigResponseDto that = (AnalyticsConfigResponseDto) o;
    return enabled == that.enabled && Objects.equals(osanoScriptUrl, that.osanoScriptUrl);
  }

  @Override
  public String toString() {
    return "AnalyticsConfigResponseDto(enabled="
        + isEnabled()
        + ", osanoScriptUrl="
        + getOsanoScriptUrl()
        + ")";
  }
}
