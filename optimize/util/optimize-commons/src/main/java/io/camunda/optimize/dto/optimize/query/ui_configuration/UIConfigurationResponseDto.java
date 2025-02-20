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
  private Map<AppName, String> webappsLinks; // links for the app switcher
  private String notificationsUrl;
  private boolean logoutHidden;
  private int maxNumDataSourcesForReport;
  private Integer exportCsvLimit;
  private DatabaseType optimizeDatabase;
  private boolean validLicense;
  private String licenseType;
  private boolean isCommercial;
  private String expiresAt;

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
      final Map<AppName, String> webappsLinks,
      final String notificationsUrl,
      final boolean logoutHidden,
      final int maxNumDataSourcesForReport,
      final Integer exportCsvLimit,
      final DatabaseType optimizeDatabase,
      final boolean validLicense,
      final String licenseType,
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
    this.webappsLinks = webappsLinks;
    this.notificationsUrl = notificationsUrl;
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

  public boolean isValidLicense() {
    return validLicense;
  }

  public void setValidLicense(final boolean validLicense) {
    this.validLicense = validLicense;
  }

  public String getLicenseType() {
    return licenseType;
  }

  public void setLicenseType(final String licenseType) {
    this.licenseType = licenseType;
  }

  public boolean isCommercial() {
    return isCommercial;
  }

  public void setCommercial(final boolean isCommercial) {
    this.isCommercial = isCommercial;
  }

  public String getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(final String expiresAt) {
    this.expiresAt = expiresAt;
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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
        + ", webappsLinks="
        + getWebappsLinks()
        + ", notificationsUrl="
        + getNotificationsUrl()
        + ", logoutHidden="
        + isLogoutHidden()
        + ", maxNumDataSourcesForReport="
        + getMaxNumDataSourcesForReport()
        + ", exportCsvLimit="
        + getExportCsvLimit()
        + ", optimizeDatabase="
        + getOptimizeDatabase()
        + ", validLicense="
        + isValidLicense()
        + ", licenseType="
        + getLicenseType()
        + ", mixpanel="
        + getMixpanel()
        + ", onboarding="
        + getOnboarding()
        + ")";
  }
}
