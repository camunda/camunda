/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.camunda.optimize.dto.optimize.ReportConstants.HYPER_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

public enum ResultType {
  @JsonProperty(MAP_RESULT_TYPE)
  MAP,
  @JsonProperty(HYPER_MAP_RESULT_TYPE)
  HYPER_MAP,
  @JsonProperty(NUMBER_RESULT_TYPE)
  NUMBER,
  @JsonProperty(RAW_RESULT_TYPE)
  RAW,
  ;
}
