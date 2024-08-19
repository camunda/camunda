/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.result;

import static io.camunda.optimize.service.security.util.LocalDateUtil.atSameTimezoneOffsetDateTime;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.service.export.CSVUtils;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

public class RawDataCommandResult<T extends RawDataInstanceDto>
    extends CommandEvaluationResult<List<T>> {

  public RawDataCommandResult(final ReportDataDto reportData) {
    super(reportData);
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }
  }

  public RawDataCommandResult(final List<T> data, final SingleReportDataDto reportData) {
    super(Collections.singletonList(MeasureDto.of(data)), reportData);
    if (data == null) {
      throw new IllegalArgumentException("data cannot be null");
    }
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }
  }

  public RawDataCommandResult() {}

  @Override
  @SuppressWarnings(UNCHECKED_CAST)
  public List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone) {
    return getResultAsCsv(limit, offset, timezone, true);
  }

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public List<String[]> getResultAsCsv(
      final Integer limit,
      final Integer offset,
      final ZoneId timezone,
      final boolean includeNewVariables) {
    final SingleReportDataDto singleReportData = getReportDataAs(SingleReportDataDto.class);
    final List<? extends RawDataInstanceDto> rawData = getFirstMeasureData();
    if (rawData.isEmpty()) {
      return CSVUtils.mapRawProcessReportInstances(
          Collections.emptyList(),
          limit,
          offset,
          singleReportData.getConfiguration().getTableColumns(),
          true);
    } else if (rawData.get(0) instanceof RawDataProcessInstanceDto) {
      final List<RawDataProcessInstanceDto> rawProcessData =
          (List<RawDataProcessInstanceDto>) rawData;
      rawProcessData.forEach(
          raw -> {
            raw.setStartDate(atSameTimezoneOffsetDateTime(raw.getStartDate(), timezone));
            raw.setEndDate(atSameTimezoneOffsetDateTime(raw.getEndDate(), timezone));
          });
      return CSVUtils.mapRawProcessReportInstances(
          rawProcessData,
          limit,
          offset,
          singleReportData.getConfiguration().getTableColumns(),
          includeNewVariables);
    } else {
      final List<RawDataDecisionInstanceDto> rawDecisionData =
          (List<RawDataDecisionInstanceDto>) rawData;
      rawDecisionData.forEach(
          raw ->
              raw.setEvaluationDateTime(
                  atSameTimezoneOffsetDateTime(raw.getEvaluationDateTime(), timezone)));
      return CSVUtils.mapRawDecisionReportInstances(
          rawDecisionData, limit, offset, singleReportData.getConfiguration().getTableColumns());
    }
  }
}
