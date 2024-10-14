/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

public class WebappsEndpointDto {

  private String endpoint;
  private String engineName;

  public WebappsEndpointDto() {}

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(final String engineName) {
    this.engineName = engineName;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof WebappsEndpointDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $endpoint = getEndpoint();
    result = result * PRIME + ($endpoint == null ? 43 : $endpoint.hashCode());
    final Object $engineName = getEngineName();
    result = result * PRIME + ($engineName == null ? 43 : $engineName.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof WebappsEndpointDto)) {
      return false;
    }
    final WebappsEndpointDto other = (WebappsEndpointDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$endpoint = getEndpoint();
    final Object other$endpoint = other.getEndpoint();
    if (this$endpoint == null ? other$endpoint != null : !this$endpoint.equals(other$endpoint)) {
      return false;
    }
    final Object this$engineName = getEngineName();
    final Object other$engineName = other.getEngineName();
    if (this$engineName == null
        ? other$engineName != null
        : !this$engineName.equals(other$engineName)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "WebappsEndpointDto(endpoint=" + getEndpoint() + ", engineName=" + getEngineName() + ")";
  }
}
