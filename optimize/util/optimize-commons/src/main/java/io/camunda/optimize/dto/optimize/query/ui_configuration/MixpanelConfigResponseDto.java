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
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $apiHost = getApiHost();
    result = result * PRIME + ($apiHost == null ? 43 : $apiHost.hashCode());
    final Object $token = getToken();
    result = result * PRIME + ($token == null ? 43 : $token.hashCode());
    final Object $organizationId = getOrganizationId();
    result = result * PRIME + ($organizationId == null ? 43 : $organizationId.hashCode());
    final Object $osanoScriptUrl = getOsanoScriptUrl();
    result = result * PRIME + ($osanoScriptUrl == null ? 43 : $osanoScriptUrl.hashCode());
    final Object $stage = getStage();
    result = result * PRIME + ($stage == null ? 43 : $stage.hashCode());
    final Object $clusterId = getClusterId();
    result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MixpanelConfigResponseDto)) {
      return false;
    }
    final MixpanelConfigResponseDto other = (MixpanelConfigResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$apiHost = getApiHost();
    final Object other$apiHost = other.getApiHost();
    if (this$apiHost == null ? other$apiHost != null : !this$apiHost.equals(other$apiHost)) {
      return false;
    }
    final Object this$token = getToken();
    final Object other$token = other.getToken();
    if (this$token == null ? other$token != null : !this$token.equals(other$token)) {
      return false;
    }
    final Object this$organizationId = getOrganizationId();
    final Object other$organizationId = other.getOrganizationId();
    if (this$organizationId == null
        ? other$organizationId != null
        : !this$organizationId.equals(other$organizationId)) {
      return false;
    }
    final Object this$osanoScriptUrl = getOsanoScriptUrl();
    final Object other$osanoScriptUrl = other.getOsanoScriptUrl();
    if (this$osanoScriptUrl == null
        ? other$osanoScriptUrl != null
        : !this$osanoScriptUrl.equals(other$osanoScriptUrl)) {
      return false;
    }
    final Object this$stage = getStage();
    final Object other$stage = other.getStage();
    if (this$stage == null ? other$stage != null : !this$stage.equals(other$stage)) {
      return false;
    }
    final Object this$clusterId = getClusterId();
    final Object other$clusterId = other.getClusterId();
    if (this$clusterId == null
        ? other$clusterId != null
        : !this$clusterId.equals(other$clusterId)) {
      return false;
    }
    return true;
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
