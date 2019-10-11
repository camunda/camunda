/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportHyperMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ReportMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.HYPER_MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ReportMapResult.class, name = MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = ProcessReportHyperMapResult.class, name = HYPER_MAP_RESULT_TYPE),
  @JsonSubTypes.Type(value = NumberResultDto.class, name = NUMBER_RESULT_TYPE),
  @JsonSubTypes.Type(value = RawDataProcessReportResultDto.class, name = RAW_RESULT_TYPE),
})
@Data
public abstract class SingleReportResultDto implements ReportResultDto {

  @Getter
  @Setter
  protected long instanceCount;
}
