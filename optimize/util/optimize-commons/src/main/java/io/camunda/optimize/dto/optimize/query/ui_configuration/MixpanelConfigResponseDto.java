/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

public class MixpanelConfigResponseDto {

  private boolean enabled;
  private String apiHost;
  private String token;
  private String organizationId;
  private String osanoScriptUrl;
  private String stage;
  private String clusterId;

  public MixpanelConfigResponseDto(
      final boolean enabled,
      final String apiHost,
      final String token,
      final String organizationId,
      final String osanoScriptUrl,
      final String stage,
      final String clusterId) {
    this.enabled = enabled;
    this.apiHost = apiHost;
    this.token = token;
    this.organizationId = organizationId;
    this.osanoScriptUrl = osanoScriptUrl;
    this.stage = stage;
    this.clusterId = clusterId;
  }

  public MixpanelConfigResponseDto() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getApiHost() {
    return apiHost;
  }

  public void setApiHost(final String apiHost) {
    this.apiHost = apiHost;
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }

  public String getOsanoScriptUrl() {
    return osanoScriptUrl;
  }

  public void setOsanoScriptUrl(final String osanoScriptUrl) {
    this.osanoScriptUrl = osanoScriptUrl;
  }

  public String getStage() {
    return stage;
  }

  public void setStage(final String stage) {
    this.stage = stage;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelConfigResponseDto;
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
    return "MixpanelConfigResponseDto(enabled="
        + isEnabled()
        + ", apiHost="
        + getApiHost()
        + ", token="
        + getToken()
        + ", organizationId="
        + getOrganizationId()
        + ", osanoScriptUrl="
        + getOsanoScriptUrl()
        + ", stage="
        + getStage()
        + ", clusterId="
        + getClusterId()
        + ")";
  }
}
