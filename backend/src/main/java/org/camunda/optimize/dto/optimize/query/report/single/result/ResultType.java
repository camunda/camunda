/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.camunda.optimize.dto.optimize.ReportConstants.DURATION_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DURATION_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

public enum ResultType {
  @JsonProperty(FREQUENCY_MAP_RESULT_TYPE)
  FREQUENCY_MAP,
  @JsonProperty(DURATION_MAP_RESULT_TYPE)
  DURATION_MAP,
  @JsonProperty(FREQUENCY_NUMBER_RESULT_TYPE)
  FREQUENCY_NUMBER,
  @JsonProperty(DURATION_NUMBER_RESULT_TYPE)
  DURATION_NUMBER,
  @JsonProperty(RAW_RESULT_TYPE)
  RAW,
  ;
}
