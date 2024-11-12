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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
