/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.ui;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;

public class UIConfiguration {

  private static final int DATA_SOURCES_LIMIT_FOR_REPORT_MINIMUM_VAL = 1;
  private static final int DATA_SOURCES_LIMIT_FOR_REPORT_MAXIMUM_VAL = 1024;

  private boolean logoutHidden;
  private String mixpanelToken;
  private int maxNumDataSourcesForReport;
  private boolean userTaskAssigneeAnalyticsEnabled;
  private String consoleUrl;
  private String modelerUrl;

  public UIConfiguration() {}

  public void validate() {
    if (maxNumDataSourcesForReport < DATA_SOURCES_LIMIT_FOR_REPORT_MINIMUM_VAL) {
      throw new OptimizeConfigurationException(
          "Cannot configure fewer than "
              + DATA_SOURCES_LIMIT_FOR_REPORT_MINIMUM_VAL
              + " number of max data sources for report. The configured limit is "
              + maxNumDataSourcesForReport);
    }
    if (maxNumDataSourcesForReport >= DATA_SOURCES_LIMIT_FOR_REPORT_MAXIMUM_VAL) {
      throw new OptimizeConfigurationException(
          "Cannot configure more than "
              + DATA_SOURCES_LIMIT_FOR_REPORT_MAXIMUM_VAL
              + " number of max data sources for report. The configured limit is "
              + maxNumDataSourcesForReport);
    }
  }

  public boolean isLogoutHidden() {
    return logoutHidden;
  }

  public void setLogoutHidden(final boolean logoutHidden) {
    this.logoutHidden = logoutHidden;
  }

  public String getMixpanelToken() {
    return mixpanelToken;
  }

  public void setMixpanelToken(final String mixpanelToken) {
    this.mixpanelToken = mixpanelToken;
  }

  public int getMaxNumDataSourcesForReport() {
    return maxNumDataSourcesForReport;
  }

  public void setMaxNumDataSourcesForReport(final int maxNumDataSourcesForReport) {
    this.maxNumDataSourcesForReport = maxNumDataSourcesForReport;
  }

  public boolean isUserTaskAssigneeAnalyticsEnabled() {
    return userTaskAssigneeAnalyticsEnabled;
  }

  public void setUserTaskAssigneeAnalyticsEnabled(final boolean userTaskAssigneeAnalyticsEnabled) {
    this.userTaskAssigneeAnalyticsEnabled = userTaskAssigneeAnalyticsEnabled;
  }

  public String getConsoleUrl() {
    return consoleUrl;
  }

  public void setConsoleUrl(final String consoleUrl) {
    this.consoleUrl = consoleUrl;
  }

  public String getModelerUrl() {
    return modelerUrl;
  }

  public void setModelerUrl(final String modelerUrl) {
    this.modelerUrl = modelerUrl;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UIConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isLogoutHidden() ? 79 : 97);
    final Object $mixpanelToken = getMixpanelToken();
    result = result * PRIME + ($mixpanelToken == null ? 43 : $mixpanelToken.hashCode());
    result = result * PRIME + getMaxNumDataSourcesForReport();
    result = result * PRIME + (isUserTaskAssigneeAnalyticsEnabled() ? 79 : 97);
    final Object $consoleUrl = getConsoleUrl();
    result = result * PRIME + ($consoleUrl == null ? 43 : $consoleUrl.hashCode());
    final Object $modelerUrl = getModelerUrl();
    result = result * PRIME + ($modelerUrl == null ? 43 : $modelerUrl.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UIConfiguration)) {
      return false;
    }
    final UIConfiguration other = (UIConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isLogoutHidden() != other.isLogoutHidden()) {
      return false;
    }
    final Object this$mixpanelToken = getMixpanelToken();
    final Object other$mixpanelToken = other.getMixpanelToken();
    if (this$mixpanelToken == null
        ? other$mixpanelToken != null
        : !this$mixpanelToken.equals(other$mixpanelToken)) {
      return false;
    }
    if (getMaxNumDataSourcesForReport() != other.getMaxNumDataSourcesForReport()) {
      return false;
    }
    if (isUserTaskAssigneeAnalyticsEnabled() != other.isUserTaskAssigneeAnalyticsEnabled()) {
      return false;
    }
    final Object this$consoleUrl = getConsoleUrl();
    final Object other$consoleUrl = other.getConsoleUrl();
    if (this$consoleUrl == null
        ? other$consoleUrl != null
        : !this$consoleUrl.equals(other$consoleUrl)) {
      return false;
    }
    final Object this$modelerUrl = getModelerUrl();
    final Object other$modelerUrl = other.getModelerUrl();
    if (this$modelerUrl == null
        ? other$modelerUrl != null
        : !this$modelerUrl.equals(other$modelerUrl)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UIConfiguration(logoutHidden="
        + isLogoutHidden()
        + ", mixpanelToken="
        + getMixpanelToken()
        + ", maxNumDataSourcesForReport="
        + getMaxNumDataSourcesForReport()
        + ", userTaskAssigneeAnalyticsEnabled="
        + isUserTaskAssigneeAnalyticsEnabled()
        + ", consoleUrl="
        + getConsoleUrl()
        + ", modelerUrl="
        + getModelerUrl()
        + ")";
  }
}
