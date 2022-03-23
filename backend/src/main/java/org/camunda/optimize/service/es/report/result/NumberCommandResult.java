/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.result;

import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.service.export.CSVUtils;

import java.time.ZoneId;
import java.util.LinkedList;
import java.util.List;

public class NumberCommandResult extends CommandEvaluationResult<Double> {

  public NumberCommandResult(@NonNull final SingleReportDataDto reportData) {
    super(reportData);
  }

  public NumberCommandResult(@NonNull final List<MeasureDto<Double>> measures,
                             @NonNull final SingleReportDataDto reportData) {
    super(measures, reportData);
  }

  public NumberCommandResult(final long instanceCount,
                             final long instanceCountWithoutFilters,
                             @NonNull final List<MeasureDto<Double>> measures,
                             @NonNull final SingleReportDataDto reportData) {
    super(instanceCount, instanceCountWithoutFilters, measures, reportData);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    if (getReportDataAs(SingleReportDataDto.class).getViewProperties().contains(ViewProperty.FREQUENCY)) {
      return frequencyNumberAsCsv();
    } else {
      return durationNumberAsCsv();
    }
  }

  @Override
  public ResultType getType() {
    return ResultType.NUMBER;
  }

  private List<String[]> frequencyNumberAsCsv() {
    final List<String[]> csvStrings = new LinkedList<>();
    csvStrings.add(new String[]{String.valueOf(getFirstMeasureData())});

    final String normalizedCommandKey = getViewIdentifier(getReportDataAs(SingleReportDataDto.class));
    final String[] header = new String[]{normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  private List<String[]> durationNumberAsCsv() {
    final List<String[]> csvStrings = new LinkedList<>();
    Double result = getFirstMeasureData();
    csvStrings.add(new String[]{String.valueOf(result)});

    final SingleReportDataDto singleReportData = getReportDataAs(SingleReportDataDto.class);
    final String normalizedCommandKey = getViewIdentifier(singleReportData);
    final String[] operations = new String[]{
      CSVUtils.mapAggregationType(singleReportData.getConfiguration().getAggregationTypes().iterator().next())
    };
    csvStrings.add(0, operations);
    final String[] header = new String[]{normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }
}
