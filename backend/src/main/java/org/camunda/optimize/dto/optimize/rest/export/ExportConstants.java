/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExportConstants {
  public static final String SINGLE_PROCESS_REPORT_STRING = "single_process_report";
  public static final String SINGLE_DECISION_REPORT_STRING = "single_decision_report";
  public static final String COMBINED_REPORT = "combined_report";
  public static final String DASHBOARD = "dashboard";
}
