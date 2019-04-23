/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.DURATION_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DURATION_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.FREQUENCY_NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProcessCountReportMapResultDto.class, name = FREQUENCY_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessDurationReportMapResultDto.class, name = DURATION_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessReportNumberResultDto.class, name = FREQUENCY_NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessDurationReportNumberResultDto.class, name = DURATION_NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataProcessReportResultDto.class, name = RAW_RESULT_TYPE),
})
public abstract class ProcessReportResultDto implements ReportResultDto {

  @Getter @Setter protected long processInstanceCount;
}
