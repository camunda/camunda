/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.OptimizeProfile;
import java.util.List;
import java.util.Map;

public class UIConfigurationResponseDto {

  private boolean emailEnabled;
  private boolean sharingEnabled;
  private boolean tenantsAvailable;
  private boolean userSearchAvailable;
  private boolean userTaskAssigneeAnalyticsEnabled;
  private String optimizeVersion;
  private String optimizeDocsVersion;
  private boolean isEnterpriseMode;
  private OptimizeProfile optimizeProfile;
  private Map<String, WebappsEndpointDto> webappsEndpoints;
  private Map<AppName, String> webappsLinks; // links for the app switcher
  private String notificationsUrl;
  private List<String> webhooks;
  private boolean logoutHidden;
  private int maxNumDataSourcesForReport;
  private Integer exportCsvLimit;
  private DatabaseType optimizeDatabase;

  private MixpanelConfigResponseDto mixpanel = new MixpanelConfigResponseDto();

  private OnboardingResponseDto onboarding = new OnboardingResponseDto();

  public UIConfigurationResponseDto(
      final boolean emailEnabled,
      final boolean sharingEnabled,
      final boolean tenantsAvailable,
      final boolean userSearchAvailable,
      final boolean userTaskAssigneeAnalyticsEnabled,
      final String optimizeVersion,
      final String optimizeDocsVersion,
      final boolean isEnterpriseMode,
      final OptimizeProfile optimizeProfile,
      final Map<String, WebappsEndpointDto> webappsEndpoints,
      final Map<AppName, String> webappsLinks,
      final String notificationsUrl,
      final List<String> webhooks,
      final boolean logoutHidden,
      final int maxNumDataSourcesForReport,
      final Integer exportCsvLimit,
      final DatabaseType optimizeDatabase,
      final MixpanelConfigResponseDto mixpanel,
      final OnboardingResponseDto onboarding) {
    this.emailEnabled = emailEnabled;
    this.sharingEnabled = sharingEnabled;
    this.tenantsAvailable = tenantsAvailable;
    this.userSearchAvailable = userSearchAvailable;
    this.userTaskAssigneeAnalyticsEnabled = userTaskAssigneeAnalyticsEnabled;
    this.optimizeVersion = optimizeVersion;
    this.optimizeDocsVersion = optimizeDocsVersion;
    this.isEnterpriseMode = isEnterpriseMode;
    this.optimizeProfile = optimizeProfile;
    this.webappsEndpoints = webappsEndpoints;
    this.webappsLinks = webappsLinks;
    this.notificationsUrl = notificationsUrl;
    this.webhooks = webhooks;
    this.logoutHidden = logoutHidden;
    this.maxNumDataSourcesForReport = maxNumDataSourcesForReport;
    this.exportCsvLimit = exportCsvLimit;
    this.optimizeDatabase = optimizeDatabase;
    this.mixpanel = mixpanel;
    this.onboarding = onboarding;
  }

  public UIConfigurationResponseDto() {}

  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  public void setEmailEnabled(final boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public boolean isSharingEnabled() {
    return sharingEnabled;
  }

  public void setSharingEnabled(final boolean sharingEnabled) {
    this.sharingEnabled = sharingEnabled;
  }

  public boolean isTenantsAvailable() {
    return tenantsAvailable;
  }

  public void setTenantsAvailable(final boolean tenantsAvailable) {
    this.tenantsAvailable = tenantsAvailable;
  }

  public boolean isUserSearchAvailable() {
    return userSearchAvailable;
  }

  public void setUserSearchAvailable(final boolean userSearchAvailable) {
    this.userSearchAvailable = userSearchAvailable;
  }

  public boolean isUserTaskAssigneeAnalyticsEnabled() {
    return userTaskAssigneeAnalyticsEnabled;
  }

  public void setUserTaskAssigneeAnalyticsEnabled(final boolean userTaskAssigneeAnalyticsEnabled) {
    this.userTaskAssigneeAnalyticsEnabled = userTaskAssigneeAnalyticsEnabled;
  }

  public String getOptimizeVersion() {
    return optimizeVersion;
  }

  public void setOptimizeVersion(final String optimizeVersion) {
    this.optimizeVersion = optimizeVersion;
  }

  public String getOptimizeDocsVersion() {
    return optimizeDocsVersion;
  }

  public void setOptimizeDocsVersion(final String optimizeDocsVersion) {
    this.optimizeDocsVersion = optimizeDocsVersion;
  }

  public boolean isEnterpriseMode() {
    return isEnterpriseMode;
  }

  public void setEnterpriseMode(final boolean isEnterpriseMode) {
    this.isEnterpriseMode = isEnterpriseMode;
  }

  public OptimizeProfile getOptimizeProfile() {
    return optimizeProfile;
  }

  public void setOptimizeProfile(final OptimizeProfile optimizeProfile) {
    this.optimizeProfile = optimizeProfile;
  }

  public Map<String, WebappsEndpointDto> getWebappsEndpoints() {
    return webappsEndpoints;
  }

  public void setWebappsEndpoints(final Map<String, WebappsEndpointDto> webappsEndpoints) {
    this.webappsEndpoints = webappsEndpoints;
  }

  public Map<AppName, String> getWebappsLinks() {
    return webappsLinks;
  }

  public void setWebappsLinks(final Map<AppName, String> webappsLinks) {
    this.webappsLinks = webappsLinks;
  }

  public String getNotificationsUrl() {
    return notificationsUrl;
  }

  public void setNotificationsUrl(final String notificationsUrl) {
    this.notificationsUrl = notificationsUrl;
  }

  public List<String> getWebhooks() {
    return webhooks;
  }

  public void setWebhooks(final List<String> webhooks) {
    this.webhooks = webhooks;
  }

  public boolean isLogoutHidden() {
    return logoutHidden;
  }

  public void setLogoutHidden(final boolean logoutHidden) {
    this.logoutHidden = logoutHidden;
  }

  public int getMaxNumDataSourcesForReport() {
    return maxNumDataSourcesForReport;
  }

  public void setMaxNumDataSourcesForReport(final int maxNumDataSourcesForReport) {
    this.maxNumDataSourcesForReport = maxNumDataSourcesForReport;
  }

  public Integer getExportCsvLimit() {
    return exportCsvLimit;
  }

  public void setExportCsvLimit(final Integer exportCsvLimit) {
    this.exportCsvLimit = exportCsvLimit;
  }

  public DatabaseType getOptimizeDatabase() {
    return optimizeDatabase;
  }

  public void setOptimizeDatabase(final DatabaseType optimizeDatabase) {
    this.optimizeDatabase = optimizeDatabase;
  }

  public MixpanelConfigResponseDto getMixpanel() {
    return mixpanel;
  }

  public void setMixpanel(final MixpanelConfigResponseDto mixpanel) {
    this.mixpanel = mixpanel;
  }

  public OnboardingResponseDto getOnboarding() {
    return onboarding;
  }

  public void setOnboarding(final OnboardingResponseDto onboarding) {
    this.onboarding = onboarding;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UIConfigurationResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEmailEnabled() ? 79 : 97);
    result = result * PRIME + (isSharingEnabled() ? 79 : 97);
    result = result * PRIME + (isTenantsAvailable() ? 79 : 97);
    result = result * PRIME + (isUserSearchAvailable() ? 79 : 97);
    result = result * PRIME + (isUserTaskAssigneeAnalyticsEnabled() ? 79 : 97);
    final Object $optimizeVersion = getOptimizeVersion();
    result = result * PRIME + ($optimizeVersion == null ? 43 : $optimizeVersion.hashCode());
    final Object $optimizeDocsVersion = getOptimizeDocsVersion();
    result = result * PRIME + ($optimizeDocsVersion == null ? 43 : $optimizeDocsVersion.hashCode());
    result = result * PRIME + (isEnterpriseMode() ? 79 : 97);
    final Object $optimizeProfile = getOptimizeProfile();
    result = result * PRIME + ($optimizeProfile == null ? 43 : $optimizeProfile.hashCode());
    final Object $webappsEndpoints = getWebappsEndpoints();
    result = result * PRIME + ($webappsEndpoints == null ? 43 : $webappsEndpoints.hashCode());
    final Object $webappsLinks = getWebappsLinks();
    result = result * PRIME + ($webappsLinks == null ? 43 : $webappsLinks.hashCode());
    final Object $notificationsUrl = getNotificationsUrl();
    result = result * PRIME + ($notificationsUrl == null ? 43 : $notificationsUrl.hashCode());
    final Object $webhooks = getWebhooks();
    result = result * PRIME + ($webhooks == null ? 43 : $webhooks.hashCode());
    result = result * PRIME + (isLogoutHidden() ? 79 : 97);
    result = result * PRIME + getMaxNumDataSourcesForReport();
    final Object $exportCsvLimit = getExportCsvLimit();
    result = result * PRIME + ($exportCsvLimit == null ? 43 : $exportCsvLimit.hashCode());
    final Object $optimizeDatabase = getOptimizeDatabase();
    result = result * PRIME + ($optimizeDatabase == null ? 43 : $optimizeDatabase.hashCode());
    final Object $mixpanel = getMixpanel();
    result = result * PRIME + ($mixpanel == null ? 43 : $mixpanel.hashCode());
    final Object $onboarding = getOnboarding();
    result = result * PRIME + ($onboarding == null ? 43 : $onboarding.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UIConfigurationResponseDto)) {
      return false;
    }
    final UIConfigurationResponseDto other = (UIConfigurationResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEmailEnabled() != other.isEmailEnabled()) {
      return false;
    }
    if (isSharingEnabled() != other.isSharingEnabled()) {
      return false;
    }
    if (isTenantsAvailable() != other.isTenantsAvailable()) {
      return false;
    }
    if (isUserSearchAvailable() != other.isUserSearchAvailable()) {
      return false;
    }
    if (isUserTaskAssigneeAnalyticsEnabled() != other.isUserTaskAssigneeAnalyticsEnabled()) {
      return false;
    }
    final Object this$optimizeVersion = getOptimizeVersion();
    final Object other$optimizeVersion = other.getOptimizeVersion();
    if (this$optimizeVersion == null
        ? other$optimizeVersion != null
        : !this$optimizeVersion.equals(other$optimizeVersion)) {
      return false;
    }
    final Object this$optimizeDocsVersion = getOptimizeDocsVersion();
    final Object other$optimizeDocsVersion = other.getOptimizeDocsVersion();
    if (this$optimizeDocsVersion == null
        ? other$optimizeDocsVersion != null
        : !this$optimizeDocsVersion.equals(other$optimizeDocsVersion)) {
      return false;
    }
    if (isEnterpriseMode() != other.isEnterpriseMode()) {
      return false;
    }
    final Object this$optimizeProfile = getOptimizeProfile();
    final Object other$optimizeProfile = other.getOptimizeProfile();
    if (this$optimizeProfile == null
        ? other$optimizeProfile != null
        : !this$optimizeProfile.equals(other$optimizeProfile)) {
      return false;
    }
    final Object this$webappsEndpoints = getWebappsEndpoints();
    final Object other$webappsEndpoints = other.getWebappsEndpoints();
    if (this$webappsEndpoints == null
        ? other$webappsEndpoints != null
        : !this$webappsEndpoints.equals(other$webappsEndpoints)) {
      return false;
    }
    final Object this$webappsLinks = getWebappsLinks();
    final Object other$webappsLinks = other.getWebappsLinks();
    if (this$webappsLinks == null
        ? other$webappsLinks != null
        : !this$webappsLinks.equals(other$webappsLinks)) {
      return false;
    }
    final Object this$notificationsUrl = getNotificationsUrl();
    final Object other$notificationsUrl = other.getNotificationsUrl();
    if (this$notificationsUrl == null
        ? other$notificationsUrl != null
        : !this$notificationsUrl.equals(other$notificationsUrl)) {
      return false;
    }
    final Object this$webhooks = getWebhooks();
    final Object other$webhooks = other.getWebhooks();
    if (this$webhooks == null ? other$webhooks != null : !this$webhooks.equals(other$webhooks)) {
      return false;
    }
    if (isLogoutHidden() != other.isLogoutHidden()) {
      return false;
    }
    if (getMaxNumDataSourcesForReport() != other.getMaxNumDataSourcesForReport()) {
      return false;
    }
    final Object this$exportCsvLimit = getExportCsvLimit();
    final Object other$exportCsvLimit = other.getExportCsvLimit();
    if (this$exportCsvLimit == null
        ? other$exportCsvLimit != null
        : !this$exportCsvLimit.equals(other$exportCsvLimit)) {
      return false;
    }
    final Object this$optimizeDatabase = getOptimizeDatabase();
    final Object other$optimizeDatabase = other.getOptimizeDatabase();
    if (this$optimizeDatabase == null
        ? other$optimizeDatabase != null
        : !this$optimizeDatabase.equals(other$optimizeDatabase)) {
      return false;
    }
    final Object this$mixpanel = getMixpanel();
    final Object other$mixpanel = other.getMixpanel();
    if (this$mixpanel == null ? other$mixpanel != null : !this$mixpanel.equals(other$mixpanel)) {
      return false;
    }
    final Object this$onboarding = getOnboarding();
    final Object other$onboarding = other.getOnboarding();
    if (this$onboarding == null
        ? other$onboarding != null
        : !this$onboarding.equals(other$onboarding)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UIConfigurationResponseDto(emailEnabled="
        + isEmailEnabled()
        + ", sharingEnabled="
        + isSharingEnabled()
        + ", tenantsAvailable="
        + isTenantsAvailable()
        + ", userSearchAvailable="
        + isUserSearchAvailable()
        + ", userTaskAssigneeAnalyticsEnabled="
        + isUserTaskAssigneeAnalyticsEnabled()
        + ", optimizeVersion="
        + getOptimizeVersion()
        + ", optimizeDocsVersion="
        + getOptimizeDocsVersion()
        + ", isEnterpriseMode="
        + isEnterpriseMode()
        + ", optimizeProfile="
        + getOptimizeProfile()
        + ", webappsEndpoints="
        + getWebappsEndpoints()
        + ", webappsLinks="
        + getWebappsLinks()
        + ", notificationsUrl="
        + getNotificationsUrl()
        + ", webhooks="
        + getWebhooks()
        + ", logoutHidden="
        + isLogoutHidden()
        + ", maxNumDataSourcesForReport="
        + getMaxNumDataSourcesForReport()
        + ", exportCsvLimit="
        + getExportCsvLimit()
        + ", optimizeDatabase="
        + getOptimizeDatabase()
        + ", mixpanel="
        + getMixpanel()
        + ", onboarding="
        + getOnboarding()
        + ")";
  }
}
