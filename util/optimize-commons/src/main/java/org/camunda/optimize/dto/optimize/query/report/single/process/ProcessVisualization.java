/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.BADGE_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.BAR_LINE_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.BAR_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.HEAT_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.LINE_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.PIE_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.SINGLE_NUMBER_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.TABLE_VISUALIZATION;

public enum ProcessVisualization {
  NUMBER(SINGLE_NUMBER_VISUALIZATION),
  TABLE(TABLE_VISUALIZATION),
  BAR(BAR_VISUALIZATION),
  BARLINE(BAR_LINE_VISUALIZATION),
  LINE(LINE_VISUALIZATION),
  PIE(PIE_VISUALIZATION),
  BADGE(BADGE_VISUALIZATION),
  HEAT(HEAT_VISUALIZATION),
  ;

  private final String id;

  ProcessVisualization(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
