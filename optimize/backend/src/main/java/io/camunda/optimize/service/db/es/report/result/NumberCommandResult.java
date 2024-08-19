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
import io.camunda.optimize.service.export.CSVUtils;
import java.time.ZoneId;
import java.util.LinkedList;
import java.util.List;

public class NumberCommandResult extends CommandEvaluationResult<Double> {

  public NumberCommandResult(final SingleReportDataDto reportData) {
    super(reportData);
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }
  }

  public NumberCommandResult(
      final List<MeasureDto<Double>> measures, final SingleReportDataDto reportData) {
    super(measures, reportData);
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }
  }

  public NumberCommandResult(
      final long instanceCount,
      final long instanceCountWithoutFilters,
      final List<MeasureDto<Double>> measures,
      final SingleReportDataDto reportData) {
    super(instanceCount, instanceCountWithoutFilters, measures, reportData);
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }
  }

  @Override
  public List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone) {
    if (getReportDataAs(SingleReportDataDto.class)
        .getViewProperties()
        .contains(ViewProperty.FREQUENCY)) {
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
    csvStrings.add(new String[] {String.valueOf(getFirstMeasureData())});

    final String normalizedCommandKey =
        getViewIdentifier(getReportDataAs(SingleReportDataDto.class));
    final String[] header = new String[] {normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  private List<String[]> durationNumberAsCsv() {
    final List<String[]> csvStrings = new LinkedList<>();
    final Double result = getFirstMeasureData();
    csvStrings.add(new String[] {String.valueOf(result)});

    final SingleReportDataDto singleReportData = getReportDataAs(SingleReportDataDto.class);
    final String normalizedCommandKey = getViewIdentifier(singleReportData);
    final String[] operations =
        new String[] {
          CSVUtils.mapAggregationType(
              singleReportData.getConfiguration().getAggregationTypes().iterator().next())
        };
    csvStrings.add(0, operations);
    final String[] header = new String[] {normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }
}
