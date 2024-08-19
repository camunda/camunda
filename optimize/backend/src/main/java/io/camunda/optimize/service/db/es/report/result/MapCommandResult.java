/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.result;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.export.CSVUtils;
import java.time.ZoneId;
import java.util.List;

public class MapCommandResult extends CommandEvaluationResult<List<MapResultEntryDto>> {

  public MapCommandResult(
      final List<MeasureDto<List<MapResultEntryDto>>> measures,
      final SingleReportDataDto reportDataDto) {
    super(measures, reportDataDto);
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }
    if (reportDataDto == null) {
      throw new IllegalArgumentException("reportDataDto cannot be null");
    }
  }

  @Override
  public List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone) {
    final List<String[]> csvStrings = CSVUtils.map(getFirstMeasureData(), limit, offset);
    addCsvHeader(csvStrings);
    return csvStrings;
  }

  @Override
  public ResultType getType() {
    return ResultType.MAP;
  }

  public void addCsvHeader(final List<String[]> csvStrings) {
    if (getReportDataAs(SingleReportDataDto.class)
        .getViewProperties()
        .contains(ViewProperty.FREQUENCY)) {
      addFrequencyHeader(csvStrings);
    } else {
      addDurationHeader(csvStrings);
    }
  }

  private void addDurationHeader(final List<String[]> csvStrings) {
    final SingleReportDataDto singleReportData = getReportDataAs(SingleReportDataDto.class);
    final String normalizedCommandKey = getViewIdentifier(singleReportData);
    final String[] operations =
        new String[] {
          "",
          CSVUtils.mapAggregationType(
              singleReportData.getConfiguration().getAggregationTypes().iterator().next())
        };
    csvStrings.add(0, operations);
    final String[] header =
        new String[] {getGroupByIdentifier(singleReportData), normalizedCommandKey};
    csvStrings.add(0, header);
  }

  private void addFrequencyHeader(final List<String[]> csvStrings) {
    final SingleReportDataDto singleReportDataDto = getReportDataAs(SingleReportDataDto.class);
    final String normalizedCommandKey = getViewIdentifier(singleReportDataDto);
    csvStrings.add(
        0, new String[] {getGroupByIdentifier(singleReportDataDto), normalizedCommandKey});
  }
}
