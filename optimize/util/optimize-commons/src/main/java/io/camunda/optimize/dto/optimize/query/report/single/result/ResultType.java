/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.result;

import static io.camunda.optimize.dto.optimize.ReportConstants.HYPER_MAP_RESULT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ResultType {
  @JsonProperty(MAP_RESULT_TYPE)
  MAP,
  @JsonProperty(HYPER_MAP_RESULT_TYPE)
  HYPER_MAP,
  @JsonProperty(NUMBER_RESULT_TYPE)
  NUMBER,
  @JsonProperty(RAW_RESULT_TYPE)
  RAW;
}
