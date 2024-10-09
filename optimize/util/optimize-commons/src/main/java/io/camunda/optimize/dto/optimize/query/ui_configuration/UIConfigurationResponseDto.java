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
  private boolean validLicense;
  private String licenseType;

  private MixpanelConfigResponseDto mixpanel = new MixpanelConfigResponseDto();

  private OnboardingResponseDto onboarding = new OnboardingResponseDto();

  public UIConfigurationResponseDto(
      boolean emailEnabled,
      boolean sharingEnabled,
      boolean tenantsAvailable,
      boolean userSearchAvailable,
      boolean userTaskAssigneeAnalyticsEnabled,
      String optimizeVersion,
      String optimizeDocsVersion,
      boolean isEnterpriseMode,
      OptimizeProfile optimizeProfile,
      Map<String, WebappsEndpointDto> webappsEndpoints,
      Map<AppName, String> webappsLinks,
      String notificationsUrl,
      List<String> webhooks,
      boolean logoutHidden,
      int maxNumDataSourcesForReport,
      Integer exportCsvLimit,
      DatabaseType optimizeDatabase,
      boolean validLicense,
      String licenseType,
      MixpanelConfigResponseDto mixpanel,
      OnboardingResponseDto onboarding) {
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
    this.validLicense = validLicense;
    this.licenseType = licenseType;
    this.mixpanel = mixpanel;
    this.onboarding = onboarding;
  }

  public UIConfigurationResponseDto() {}

  public boolean isEmailEnabled() {
    return this.emailEnabled;
  }

  public boolean isSharingEnabled() {
    return this.sharingEnabled;
  }

  public boolean isTenantsAvailable() {
    return this.tenantsAvailable;
  }

  public boolean isUserSearchAvailable() {
    return this.userSearchAvailable;
  }

  public boolean isUserTaskAssigneeAnalyticsEnabled() {
    return this.userTaskAssigneeAnalyticsEnabled;
  }

  public String getOptimizeVersion() {
    return this.optimizeVersion;
  }

  public String getOptimizeDocsVersion() {
    return this.optimizeDocsVersion;
  }

  public boolean isEnterpriseMode() {
    return this.isEnterpriseMode;
  }

  public OptimizeProfile getOptimizeProfile() {
    return this.optimizeProfile;
  }

  public Map<String, WebappsEndpointDto> getWebappsEndpoints() {
    return this.webappsEndpoints;
  }

  public Map<AppName, String> getWebappsLinks() {
    return this.webappsLinks;
  }

  public String getNotificationsUrl() {
    return this.notificationsUrl;
  }

  public List<String> getWebhooks() {
    return this.webhooks;
  }

  public boolean isLogoutHidden() {
    return this.logoutHidden;
  }

  public int getMaxNumDataSourcesForReport() {
    return this.maxNumDataSourcesForReport;
  }

  public Integer getExportCsvLimit() {
    return this.exportCsvLimit;
  }

  public DatabaseType getOptimizeDatabase() {
    return this.optimizeDatabase;
  }

  public boolean isValidLicense() {
    return this.validLicense;
  }

  public String getLicenseType() {
    return this.licenseType;
  }

  public MixpanelConfigResponseDto getMixpanel() {
    return this.mixpanel;
  }

  public OnboardingResponseDto getOnboarding() {
    return this.onboarding;
  }

  public void setEmailEnabled(boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public void setSharingEnabled(boolean sharingEnabled) {
    this.sharingEnabled = sharingEnabled;
  }

  public void setTenantsAvailable(boolean tenantsAvailable) {
    this.tenantsAvailable = tenantsAvailable;
  }

  public void setUserSearchAvailable(boolean userSearchAvailable) {
    this.userSearchAvailable = userSearchAvailable;
  }

  public void setUserTaskAssigneeAnalyticsEnabled(boolean userTaskAssigneeAnalyticsEnabled) {
    this.userTaskAssigneeAnalyticsEnabled = userTaskAssigneeAnalyticsEnabled;
  }

  public void setOptimizeVersion(String optimizeVersion) {
    this.optimizeVersion = optimizeVersion;
  }

  public void setOptimizeDocsVersion(String optimizeDocsVersion) {
    this.optimizeDocsVersion = optimizeDocsVersion;
  }

  public void setEnterpriseMode(boolean isEnterpriseMode) {
    this.isEnterpriseMode = isEnterpriseMode;
  }

  public void setOptimizeProfile(OptimizeProfile optimizeProfile) {
    this.optimizeProfile = optimizeProfile;
  }

  public void setWebappsEndpoints(Map<String, WebappsEndpointDto> webappsEndpoints) {
    this.webappsEndpoints = webappsEndpoints;
  }

  public void setWebappsLinks(Map<AppName, String> webappsLinks) {
    this.webappsLinks = webappsLinks;
  }

  public void setNotificationsUrl(String notificationsUrl) {
    this.notificationsUrl = notificationsUrl;
  }

  public void setWebhooks(List<String> webhooks) {
    this.webhooks = webhooks;
  }

  public void setLogoutHidden(boolean logoutHidden) {
    this.logoutHidden = logoutHidden;
  }

  public void setMaxNumDataSourcesForReport(int maxNumDataSourcesForReport) {
    this.maxNumDataSourcesForReport = maxNumDataSourcesForReport;
  }

  public void setExportCsvLimit(Integer exportCsvLimit) {
    this.exportCsvLimit = exportCsvLimit;
  }

  public void setOptimizeDatabase(DatabaseType optimizeDatabase) {
    this.optimizeDatabase = optimizeDatabase;
  }

  public void setValidLicense(boolean validLicense) {
    this.validLicense = validLicense;
  }

  public void setLicenseType(String licenseType) {
    this.licenseType = licenseType;
  }

  public void setMixpanel(MixpanelConfigResponseDto mixpanel) {
    this.mixpanel = mixpanel;
  }

  public void setOnboarding(OnboardingResponseDto onboarding) {
    this.onboarding = onboarding;
  }

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
    if (this.isEmailEnabled() != other.isEmailEnabled()) {
      return false;
    }
    if (this.isSharingEnabled() != other.isSharingEnabled()) {
      return false;
    }
    if (this.isTenantsAvailable() != other.isTenantsAvailable()) {
      return false;
    }
    if (this.isUserSearchAvailable() != other.isUserSearchAvailable()) {
      return false;
    }
    if (this.isUserTaskAssigneeAnalyticsEnabled() != other.isUserTaskAssigneeAnalyticsEnabled()) {
      return false;
    }
    final Object this$optimizeVersion = this.getOptimizeVersion();
    final Object other$optimizeVersion = other.getOptimizeVersion();
    if (this$optimizeVersion == null
        ? other$optimizeVersion != null
        : !this$optimizeVersion.equals(other$optimizeVersion)) {
      return false;
    }
    final Object this$optimizeDocsVersion = this.getOptimizeDocsVersion();
    final Object other$optimizeDocsVersion = other.getOptimizeDocsVersion();
    if (this$optimizeDocsVersion == null
        ? other$optimizeDocsVersion != null
        : !this$optimizeDocsVersion.equals(other$optimizeDocsVersion)) {
      return false;
    }
    if (this.isEnterpriseMode() != other.isEnterpriseMode()) {
      return false;
    }
    final Object this$optimizeProfile = this.getOptimizeProfile();
    final Object other$optimizeProfile = other.getOptimizeProfile();
    if (this$optimizeProfile == null
        ? other$optimizeProfile != null
        : !this$optimizeProfile.equals(other$optimizeProfile)) {
      return false;
    }
    final Object this$webappsEndpoints = this.getWebappsEndpoints();
    final Object other$webappsEndpoints = other.getWebappsEndpoints();
    if (this$webappsEndpoints == null
        ? other$webappsEndpoints != null
        : !this$webappsEndpoints.equals(other$webappsEndpoints)) {
      return false;
    }
    final Object this$webappsLinks = this.getWebappsLinks();
    final Object other$webappsLinks = other.getWebappsLinks();
    if (this$webappsLinks == null
        ? other$webappsLinks != null
        : !this$webappsLinks.equals(other$webappsLinks)) {
      return false;
    }
    final Object this$notificationsUrl = this.getNotificationsUrl();
    final Object other$notificationsUrl = other.getNotificationsUrl();
    if (this$notificationsUrl == null
        ? other$notificationsUrl != null
        : !this$notificationsUrl.equals(other$notificationsUrl)) {
      return false;
    }
    final Object this$webhooks = this.getWebhooks();
    final Object other$webhooks = other.getWebhooks();
    if (this$webhooks == null ? other$webhooks != null : !this$webhooks.equals(other$webhooks)) {
      return false;
    }
    if (this.isLogoutHidden() != other.isLogoutHidden()) {
      return false;
    }
    if (this.getMaxNumDataSourcesForReport() != other.getMaxNumDataSourcesForReport()) {
      return false;
    }
    final Object this$exportCsvLimit = this.getExportCsvLimit();
    final Object other$exportCsvLimit = other.getExportCsvLimit();
    if (this$exportCsvLimit == null
        ? other$exportCsvLimit != null
        : !this$exportCsvLimit.equals(other$exportCsvLimit)) {
      return false;
    }
    final Object this$optimizeDatabase = this.getOptimizeDatabase();
    final Object other$optimizeDatabase = other.getOptimizeDatabase();
    if (this$optimizeDatabase == null
        ? other$optimizeDatabase != null
        : !this$optimizeDatabase.equals(other$optimizeDatabase)) {
      return false;
    }
    if (this.isValidLicense() != other.isValidLicense()) {
      return false;
    }
    final Object this$licenseType = this.getLicenseType();
    final Object other$licenseType = other.getLicenseType();
    if (this$licenseType == null
        ? other$licenseType != null
        : !this$licenseType.equals(other$licenseType)) {
      return false;
    }
    final Object this$mixpanel = this.getMixpanel();
    final Object other$mixpanel = other.getMixpanel();
    if (this$mixpanel == null ? other$mixpanel != null : !this$mixpanel.equals(other$mixpanel)) {
      return false;
    }
    final Object this$onboarding = this.getOnboarding();
    final Object other$onboarding = other.getOnboarding();
    if (this$onboarding == null
        ? other$onboarding != null
        : !this$onboarding.equals(other$onboarding)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UIConfigurationResponseDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (this.isEmailEnabled() ? 79 : 97);
    result = result * PRIME + (this.isSharingEnabled() ? 79 : 97);
    result = result * PRIME + (this.isTenantsAvailable() ? 79 : 97);
    result = result * PRIME + (this.isUserSearchAvailable() ? 79 : 97);
    result = result * PRIME + (this.isUserTaskAssigneeAnalyticsEnabled() ? 79 : 97);
    final Object $optimizeVersion = this.getOptimizeVersion();
    result = result * PRIME + ($optimizeVersion == null ? 43 : $optimizeVersion.hashCode());
    final Object $optimizeDocsVersion = this.getOptimizeDocsVersion();
    result = result * PRIME + ($optimizeDocsVersion == null ? 43 : $optimizeDocsVersion.hashCode());
    result = result * PRIME + (this.isEnterpriseMode() ? 79 : 97);
    final Object $optimizeProfile = this.getOptimizeProfile();
    result = result * PRIME + ($optimizeProfile == null ? 43 : $optimizeProfile.hashCode());
    final Object $webappsEndpoints = this.getWebappsEndpoints();
    result = result * PRIME + ($webappsEndpoints == null ? 43 : $webappsEndpoints.hashCode());
    final Object $webappsLinks = this.getWebappsLinks();
    result = result * PRIME + ($webappsLinks == null ? 43 : $webappsLinks.hashCode());
    final Object $notificationsUrl = this.getNotificationsUrl();
    result = result * PRIME + ($notificationsUrl == null ? 43 : $notificationsUrl.hashCode());
    final Object $webhooks = this.getWebhooks();
    result = result * PRIME + ($webhooks == null ? 43 : $webhooks.hashCode());
    result = result * PRIME + (this.isLogoutHidden() ? 79 : 97);
    result = result * PRIME + this.getMaxNumDataSourcesForReport();
    final Object $exportCsvLimit = this.getExportCsvLimit();
    result = result * PRIME + ($exportCsvLimit == null ? 43 : $exportCsvLimit.hashCode());
    final Object $optimizeDatabase = this.getOptimizeDatabase();
    result = result * PRIME + ($optimizeDatabase == null ? 43 : $optimizeDatabase.hashCode());
    result = result * PRIME + (this.isValidLicense() ? 79 : 97);
    final Object $licenseType = this.getLicenseType();
    result = result * PRIME + ($licenseType == null ? 43 : $licenseType.hashCode());
    final Object $mixpanel = this.getMixpanel();
    result = result * PRIME + ($mixpanel == null ? 43 : $mixpanel.hashCode());
    final Object $onboarding = this.getOnboarding();
    result = result * PRIME + ($onboarding == null ? 43 : $onboarding.hashCode());
    return result;
  }

  public String toString() {
    return "UIConfigurationResponseDto(emailEnabled="
        + this.isEmailEnabled()
        + ", sharingEnabled="
        + this.isSharingEnabled()
        + ", tenantsAvailable="
        + this.isTenantsAvailable()
        + ", userSearchAvailable="
        + this.isUserSearchAvailable()
        + ", userTaskAssigneeAnalyticsEnabled="
        + this.isUserTaskAssigneeAnalyticsEnabled()
        + ", optimizeVersion="
        + this.getOptimizeVersion()
        + ", optimizeDocsVersion="
        + this.getOptimizeDocsVersion()
        + ", isEnterpriseMode="
        + this.isEnterpriseMode()
        + ", optimizeProfile="
        + this.getOptimizeProfile()
        + ", webappsEndpoints="
        + this.getWebappsEndpoints()
        + ", webappsLinks="
        + this.getWebappsLinks()
        + ", notificationsUrl="
        + this.getNotificationsUrl()
        + ", webhooks="
        + this.getWebhooks()
        + ", logoutHidden="
        + this.isLogoutHidden()
        + ", maxNumDataSourcesForReport="
        + this.getMaxNumDataSourcesForReport()
        + ", exportCsvLimit="
        + this.getExportCsvLimit()
        + ", optimizeDatabase="
        + this.getOptimizeDatabase()
        + ", validLicense="
        + this.isValidLicense()
        + ", licenseType="
        + this.getLicenseType()
        + ", mixpanel="
        + this.getMixpanel()
        + ", onboarding="
        + this.getOnboarding()
        + ")";
  }
}
