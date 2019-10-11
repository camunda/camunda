/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DecisionReportMapResultDto.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = DecisionReportNumberResultDto.class, name = NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataDecisionReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class DecisionReportResultDto extends SingleReportResultDto {

}
