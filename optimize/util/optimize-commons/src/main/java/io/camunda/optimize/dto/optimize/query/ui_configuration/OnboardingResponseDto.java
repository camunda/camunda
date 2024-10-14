/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

public class OnboardingResponseDto {

  private boolean enabled;
  private String appCuesScriptUrl;
  private String orgId;
  private String clusterId;
  private String salesPlanType;

  public OnboardingResponseDto(
      final boolean enabled,
      final String appCuesScriptUrl,
      final String orgId,
      final String clusterId,
      final String salesPlanType) {
    this.enabled = enabled;
    this.appCuesScriptUrl = appCuesScriptUrl;
    this.orgId = orgId;
    this.clusterId = clusterId;
    this.salesPlanType = salesPlanType;
  }

  public OnboardingResponseDto() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getAppCuesScriptUrl() {
    return appCuesScriptUrl;
  }

  public void setAppCuesScriptUrl(final String appCuesScriptUrl) {
    this.appCuesScriptUrl = appCuesScriptUrl;
  }

  public String getOrgId() {
    return orgId;
  }

  public void setOrgId(final String orgId) {
    this.orgId = orgId;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public void setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OnboardingResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $appCuesScriptUrl = getAppCuesScriptUrl();
    result = result * PRIME + ($appCuesScriptUrl == null ? 43 : $appCuesScriptUrl.hashCode());
    final Object $orgId = getOrgId();
    result = result * PRIME + ($orgId == null ? 43 : $orgId.hashCode());
    final Object $clusterId = getClusterId();
    result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
    final Object $salesPlanType = getSalesPlanType();
    result = result * PRIME + ($salesPlanType == null ? 43 : $salesPlanType.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OnboardingResponseDto)) {
      return false;
    }
    final OnboardingResponseDto other = (OnboardingResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$appCuesScriptUrl = getAppCuesScriptUrl();
    final Object other$appCuesScriptUrl = other.getAppCuesScriptUrl();
    if (this$appCuesScriptUrl == null
        ? other$appCuesScriptUrl != null
        : !this$appCuesScriptUrl.equals(other$appCuesScriptUrl)) {
      return false;
    }
    final Object this$orgId = getOrgId();
    final Object other$orgId = other.getOrgId();
    if (this$orgId == null ? other$orgId != null : !this$orgId.equals(other$orgId)) {
      return false;
    }
    final Object this$clusterId = getClusterId();
    final Object other$clusterId = other.getClusterId();
    if (this$clusterId == null
        ? other$clusterId != null
        : !this$clusterId.equals(other$clusterId)) {
      return false;
    }
    final Object this$salesPlanType = getSalesPlanType();
    final Object other$salesPlanType = other.getSalesPlanType();
    if (this$salesPlanType == null
        ? other$salesPlanType != null
        : !this$salesPlanType.equals(other$salesPlanType)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "OnboardingResponseDto(enabled="
        + isEnabled()
        + ", appCuesScriptUrl="
        + getAppCuesScriptUrl()
        + ", orgId="
        + getOrgId()
        + ", clusterId="
        + getClusterId()
        + ", salesPlanType="
        + getSalesPlanType()
        + ")";
  }
}
