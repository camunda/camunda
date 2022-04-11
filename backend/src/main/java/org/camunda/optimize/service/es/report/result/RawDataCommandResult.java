/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.result;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.service.export.CSVUtils;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.security.util.LocalDateUtil.atSameTimezoneOffsetDateTime;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@NoArgsConstructor
public class RawDataCommandResult<T extends RawDataInstanceDto> extends CommandEvaluationResult<List<T>> {

  public RawDataCommandResult(final @NonNull ReportDataDto reportData) {
    super(reportData);
  }

  public RawDataCommandResult(@NonNull final List<T> data,
                              @NonNull final SingleReportDataDto reportData) {
    super(Collections.singletonList(MeasureDto.of(data)), reportData);
  }

  @Override
  @SuppressWarnings(UNCHECKED_CAST)
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    final SingleReportDataDto singleReportData = getReportDataAs(SingleReportDataDto.class);
    final List<? extends RawDataInstanceDto> rawData = getFirstMeasureData();
    if (rawData.isEmpty()) {
      return CSVUtils.mapRawProcessReportInstances(
        Collections.emptyList(), limit, offset, singleReportData.getConfiguration().getTableColumns()
      );
    } else if (rawData.get(0) instanceof RawDataProcessInstanceDto) {
      List<RawDataProcessInstanceDto> rawProcessData = (List<RawDataProcessInstanceDto>) rawData;
      rawProcessData.forEach(raw -> {
        raw.setStartDate(atSameTimezoneOffsetDateTime(raw.getStartDate(), timezone));
        raw.setEndDate(atSameTimezoneOffsetDateTime(raw.getEndDate(), timezone));
      });
      return CSVUtils.mapRawProcessReportInstances(
        rawProcessData, limit, offset, singleReportData.getConfiguration().getTableColumns()
      );
    } else {
      List<RawDataDecisionInstanceDto> rawDecisionData = (List<RawDataDecisionInstanceDto>) rawData;
      rawDecisionData.forEach(raw -> raw.setEvaluationDateTime(atSameTimezoneOffsetDateTime(
        raw.getEvaluationDateTime(),
        timezone
      )));
      return CSVUtils.mapRawDecisionReportInstances(
        rawDecisionData, limit, offset, singleReportData.getConfiguration().getTableColumns()
      );
    }
  }

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

}
