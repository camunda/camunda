/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class OsanoConfiguration {

  @JsonProperty("scriptUrl")
  private String scriptUrl;

  public OsanoConfiguration(final String scriptUrl) {
    this.scriptUrl = scriptUrl;
  }

  protected OsanoConfiguration() {}

  public Optional<String> getScriptUrl() {
    return Optional.ofNullable(scriptUrl);
  }

  @JsonProperty("scriptUrl")
  public void setScriptUrl(final String scriptUrl) {
    this.scriptUrl = scriptUrl;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OsanoConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $scriptUrl = getScriptUrl();
    result = result * PRIME + ($scriptUrl == null ? 43 : $scriptUrl.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OsanoConfiguration)) {
      return false;
    }
    final OsanoConfiguration other = (OsanoConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$scriptUrl = getScriptUrl();
    final Object other$scriptUrl = other.getScriptUrl();
    if (this$scriptUrl == null
        ? other$scriptUrl != null
        : !this$scriptUrl.equals(other$scriptUrl)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "OsanoConfiguration(scriptUrl=" + getScriptUrl() + ")";
  }
}
