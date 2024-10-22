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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
