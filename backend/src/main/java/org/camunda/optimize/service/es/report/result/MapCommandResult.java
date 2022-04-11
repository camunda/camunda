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
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.export.CSVUtils;

import java.time.ZoneId;
import java.util.List;

public class MapCommandResult extends CommandEvaluationResult<List<MapResultEntryDto>> {

  public MapCommandResult(@NonNull final List<MeasureDto<List<MapResultEntryDto>>> measures,
                          @NonNull final SingleReportDataDto reportDataDto) {
    super(measures, reportDataDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    final List<String[]> csvStrings = CSVUtils.map(getFirstMeasureData(), limit, offset);
    addCsvHeader(csvStrings);
    return csvStrings;
  }

  @Override
  public ResultType getType() {
    return ResultType.MAP;
  }

  public void addCsvHeader(final List<String[]> csvStrings) {
    if (getReportDataAs(SingleReportDataDto.class).getViewProperties().contains(ViewProperty.FREQUENCY)) {
      addFrequencyHeader(csvStrings);
    } else {
      addDurationHeader(csvStrings);
    }
  }

  private void addDurationHeader(final List<String[]> csvStrings) {
    final SingleReportDataDto singleReportData = getReportDataAs(SingleReportDataDto.class);
    final String normalizedCommandKey = getViewIdentifier(singleReportData);
    final String[] operations = new String[]{
      "", CSVUtils.mapAggregationType(singleReportData.getConfiguration().getAggregationTypes().iterator().next())
    };
    csvStrings.add(0, operations);
    final String[] header =
      new String[]{getGroupByIdentifier(singleReportData), normalizedCommandKey};
    csvStrings.add(0, header);
  }

  private void addFrequencyHeader(final List<String[]> csvStrings) {
    final SingleReportDataDto singleReportDataDto = getReportDataAs(SingleReportDataDto.class);
    final String normalizedCommandKey = getViewIdentifier(singleReportDataDto);
    csvStrings.add(0, new String[]{getGroupByIdentifier(singleReportDataDto), normalizedCommandKey});
  }

}
