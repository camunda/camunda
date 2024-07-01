/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.ui;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import lombok.Data;

@Data
public class UIConfiguration {

  private static final int DATA_SOURCES_LIMIT_FOR_REPORT_MINIMUM_VAL = 1;
  private static final int DATA_SOURCES_LIMIT_FOR_REPORT_MAXIMUM_VAL = 1024;

  private boolean logoutHidden;
  private String mixpanelToken;
  private int maxNumDataSourcesForReport;
  private boolean userTaskAssigneeAnalyticsEnabled;
  private String consoleUrl;
  private String modelerUrl;

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
}
