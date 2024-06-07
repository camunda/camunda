/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.ui;

import lombok.Data;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

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
