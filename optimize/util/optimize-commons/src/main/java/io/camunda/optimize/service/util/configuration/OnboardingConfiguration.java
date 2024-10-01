/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.j2objc.annotations.Property;

public class OnboardingConfiguration {

  @Property("enabled")
  private boolean enabled;

  @Property("appCuesScriptUrl")
  private String appCuesScriptUrl;

  @Property("scheduleProcessOnboardingChecks")
  private boolean scheduleProcessOnboardingChecks;

  @Property("enableOnboardingEmails")
  private boolean enableOnboardingEmails;

  @Property("intervalForCheckingTriggerForOnboardingEmails")
  private int intervalForCheckingTriggerForOnboardingEmails;

  @JsonProperty("properties")
  private Properties properties;

  public OnboardingConfiguration() {}

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

  public boolean isScheduleProcessOnboardingChecks() {
    return scheduleProcessOnboardingChecks;
  }

  public void setScheduleProcessOnboardingChecks(final boolean scheduleProcessOnboardingChecks) {
    this.scheduleProcessOnboardingChecks = scheduleProcessOnboardingChecks;
  }

  public boolean isEnableOnboardingEmails() {
    return enableOnboardingEmails;
  }

  public void setEnableOnboardingEmails(final boolean enableOnboardingEmails) {
    this.enableOnboardingEmails = enableOnboardingEmails;
  }

  public int getIntervalForCheckingTriggerForOnboardingEmails() {
    return intervalForCheckingTriggerForOnboardingEmails;
  }

  public void setIntervalForCheckingTriggerForOnboardingEmails(
      final int intervalForCheckingTriggerForOnboardingEmails) {
    this.intervalForCheckingTriggerForOnboardingEmails =
        intervalForCheckingTriggerForOnboardingEmails;
  }

  public Properties getProperties() {
    return properties;
  }

  @JsonProperty("properties")
  public void setProperties(final Properties properties) {
    this.properties = properties;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OnboardingConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $appCuesScriptUrl = getAppCuesScriptUrl();
    result = result * PRIME + ($appCuesScriptUrl == null ? 43 : $appCuesScriptUrl.hashCode());
    result = result * PRIME + (isScheduleProcessOnboardingChecks() ? 79 : 97);
    result = result * PRIME + (isEnableOnboardingEmails() ? 79 : 97);
    result = result * PRIME + getIntervalForCheckingTriggerForOnboardingEmails();
    final Object $properties = getProperties();
    result = result * PRIME + ($properties == null ? 43 : $properties.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OnboardingConfiguration)) {
      return false;
    }
    final OnboardingConfiguration other = (OnboardingConfiguration) o;
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
    if (isScheduleProcessOnboardingChecks() != other.isScheduleProcessOnboardingChecks()) {
      return false;
    }
    if (isEnableOnboardingEmails() != other.isEnableOnboardingEmails()) {
      return false;
    }
    if (getIntervalForCheckingTriggerForOnboardingEmails()
        != other.getIntervalForCheckingTriggerForOnboardingEmails()) {
      return false;
    }
    final Object this$properties = getProperties();
    final Object other$properties = other.getProperties();
    if (this$properties == null
        ? other$properties != null
        : !this$properties.equals(other$properties)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "OnboardingConfiguration(enabled="
        + isEnabled()
        + ", appCuesScriptUrl="
        + getAppCuesScriptUrl()
        + ", scheduleProcessOnboardingChecks="
        + isScheduleProcessOnboardingChecks()
        + ", enableOnboardingEmails="
        + isEnableOnboardingEmails()
        + ", intervalForCheckingTriggerForOnboardingEmails="
        + getIntervalForCheckingTriggerForOnboardingEmails()
        + ", properties="
        + getProperties()
        + ")";
  }

  public static class Properties {

    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("clusterId")
    private String clusterId;

    public Properties(final String organizationId, final String clusterId) {
      this.organizationId = organizationId;
      this.clusterId = clusterId;
    }

    protected Properties() {}

    public String getOrganizationId() {
      return organizationId;
    }

    @JsonProperty("organizationId")
    public void setOrganizationId(final String organizationId) {
      this.organizationId = organizationId;
    }

    public String getClusterId() {
      return clusterId;
    }

    @JsonProperty("clusterId")
    public void setClusterId(final String clusterId) {
      this.clusterId = clusterId;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof Properties;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $organizationId = getOrganizationId();
      result = result * PRIME + ($organizationId == null ? 43 : $organizationId.hashCode());
      final Object $clusterId = getClusterId();
      result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof Properties)) {
        return false;
      }
      final Properties other = (Properties) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$organizationId = getOrganizationId();
      final Object other$organizationId = other.getOrganizationId();
      if (this$organizationId == null
          ? other$organizationId != null
          : !this$organizationId.equals(other$organizationId)) {
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
      return "OnboardingConfiguration.Properties(organizationId="
          + getOrganizationId()
          + ", clusterId="
          + getClusterId()
          + ")";
    }
  }
}
