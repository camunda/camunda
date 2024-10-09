/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import io.camunda.optimize.service.db.es.report.result.MapCommandResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.export.CSVUtils;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

public class CombinedReportEvaluationResult extends ReportEvaluationResult {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CombinedReportEvaluationResult.class);
  private List<SingleReportEvaluationResult<?>> reportEvaluationResults;
  private long instanceCount;

  public CombinedReportEvaluationResult(
      final List<SingleReportEvaluationResult<?>> singleReportResults,
      final long instanceCount,
      final CombinedReportDefinitionRequestDto reportDefinition) {
    super(reportDefinition);
    if (singleReportResults == null) {
      throw new IllegalArgumentException("singleReportResults cannot be null");
    }
    if (reportDefinition == null) {
      throw new IllegalArgumentException("reportDefinition cannot be null");
    }

    reportEvaluationResults = new ArrayList<>(singleReportResults);
    this.instanceCount = instanceCount;
  }

  @Override
  public List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone) {
    final Optional<ResultType> resultType =
        reportEvaluationResults.stream()
            .findFirst()
            .map(thing -> thing.getFirstCommandResult().getType());
    return resultType
        .map(r -> mapCombinedReportResultsToCsvList(limit, offset, r))
        .orElseGet(
            () -> {
              log.debug(
                  "No reports to evaluate are available in the combined report. Returning empty csv instead.");
              return Collections.singletonList(new String[] {});
            });
  }

  @Override
  public PaginatedDataExportDto getResult() {
    throw new OptimizeValidationException("Combined reports cannot be exported");
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CombinedReportEvaluationResult;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $reportEvaluationResults = getReportEvaluationResults();
    result =
        result * PRIME
            + ($reportEvaluationResults == null ? 43 : $reportEvaluationResults.hashCode());
    final long $instanceCount = getInstanceCount();
    result = result * PRIME + (int) ($instanceCount >>> 32 ^ $instanceCount);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CombinedReportEvaluationResult)) {
      return false;
    }
    final CombinedReportEvaluationResult other = (CombinedReportEvaluationResult) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$reportEvaluationResults = getReportEvaluationResults();
    final Object other$reportEvaluationResults = other.getReportEvaluationResults();
    if (this$reportEvaluationResults == null
        ? other$reportEvaluationResults != null
        : !this$reportEvaluationResults.equals(other$reportEvaluationResults)) {
      return false;
    }
    if (getInstanceCount() != other.getInstanceCount()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CombinedReportEvaluationResult(reportEvaluationResults="
        + getReportEvaluationResults()
        + ", instanceCount="
        + getInstanceCount()
        + ")";
  }

  private List<String[]> mapCombinedReportResultsToCsvList(
      final Integer limit, final Integer offset, final ResultType resultType) {
    final List<String[]> csvStrings;
    switch (resultType) {
      case MAP:
        csvStrings = mapCombinedMapReportResultsToCsvList(limit, offset);
        break;
      case NUMBER:
        csvStrings = mapCombinedNumberReportResultsToCsvList();
        break;
      default:
        final String message =
            String.format(
                "Unsupported report type [%s] in combined report",
                resultType.getClass().getSimpleName());
        log.error(message);
        throw new OptimizeRuntimeException(message);
    }
    return csvStrings;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private List<String[]> mapCombinedMapReportResultsToCsvList(
      final Integer limit, final Integer offset) {
    final List<SingleReportEvaluationResult<List<MapResultEntryDto>>> mapReportResults =
        reportEvaluationResults.stream()
            .map(entry -> (SingleReportEvaluationResult<List<MapResultEntryDto>>) entry)
            .collect(Collectors.toList());
    final List<List<String[]>> allSingleReportsAsCsvList =
        extractListOfReportResultTables(limit, offset, mapReportResults);

    final String[] reportNameHeader =
        createCombinedReportHeader(
            reportEvaluationResults, r -> new String[] {r.getReportDefinition().getName(), "", ""});

    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    mergedCsvReports.add(0, reportNameHeader);

    return mergedCsvReports;
  }

  /**
   * @return a list of report result tables where each String[] entry consists of two values - label
   *     and value. A List<String[]> corresponds to a report result table with a header, e.g.
   *     flowNodes | flowNode_frequency Task A | 1 Task B | 50
   */
  private List<List<String[]>> extractListOfReportResultTables(
      final Integer limit,
      final Integer offset,
      final List<SingleReportEvaluationResult<List<MapResultEntryDto>>> mapReportResults) {
    final Set<String> allLabels = collectAllLabels(mapReportResults);
    final List<List<String[]>> reportResultTable = new ArrayList<>();
    mapReportResults.forEach(
        singleResult -> {
          final List<MapResultEntryDto> data =
              singleResult.getFirstCommandResult().getFirstMeasureData();
          final Set<String> labelsInData =
              data.stream().map(MapResultEntryDto::getLabel).collect(Collectors.toSet());
          final Sets.SetView<String> newLabels = Sets.difference(allLabels, labelsInData);
          final List<MapResultEntryDto> enrichedData = new ArrayList<>(data);
          newLabels.forEach(
              newLabel -> enrichedData.add(new MapResultEntryDto(newLabel, null, newLabel)));
          enrichedData.sort(
              Comparator.comparing(MapResultEntryDto::getLabel, String.CASE_INSENSITIVE_ORDER));
          final List<String[]> singleReportTable = CSVUtils.map(enrichedData, limit, offset);
          new MapCommandResult(
                  singleResult.getFirstCommandResult().getMeasures(),
                  (SingleReportDataDto) singleResult.getReportDefinition().getData())
              .addCsvHeader(singleReportTable);
          reportResultTable.add(singleReportTable);
        });
    return reportResultTable;
  }

  private Set<String> collectAllLabels(
      final List<SingleReportEvaluationResult<List<MapResultEntryDto>>> reportResults) {
    return reportResults.stream()
        .map(SingleReportEvaluationResult::getFirstCommandResult)
        .flatMap(r -> r.getFirstMeasureData().stream())
        .map(MapResultEntryDto::getLabel)
        .collect(Collectors.toSet());
  }

  public List<SingleReportEvaluationResult<?>> getReportEvaluationResults() {
    return reportEvaluationResults;
  }

  public void setReportEvaluationResults(
      final List<SingleReportEvaluationResult<?>> reportEvaluationResults) {
    if (reportEvaluationResults == null) {
      throw new IllegalArgumentException("reportEvaluationResults cannot be null");
    }

    this.reportEvaluationResults = reportEvaluationResults;
  }

  public long getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(final long instanceCount) {
    this.instanceCount = instanceCount;
  }

  private List<String[]> mapCombinedNumberReportResultsToCsvList() {
    final List<List<String[]>> reportResultTable =
        reportEvaluationResults.stream()
            .map(reportResult -> reportResult.getResultAsCsv(1, 0, ZoneId.systemDefault()))
            .collect(Collectors.toList());

    final String[] reportNameHeader =
        createCombinedReportHeader(
            reportEvaluationResults, r -> new String[] {r.getReportDefinition().getName(), ""});

    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(reportResultTable);
    mergedCsvReports.add(0, reportNameHeader);

    return mergedCsvReports;
  }

  private List<String[]> mergeSingleReportsToOneCsv(
      final List<List<String[]>> allSingleReportsAsCsvList) {
    return allSingleReportsAsCsvList.stream()
        .reduce(
            (l1, l2) -> {
              for (int i = 0; i < l1.size(); i++) {
                final String[] firstReportWithSeparatorColumn = ArrayUtils.addAll(l1.get(i), "");
                l1.set(i, ArrayUtils.addAll(firstReportWithSeparatorColumn, l2.get(i)));
              }
              return l1;
            })
        .orElseThrow(
            () -> {
              final String message = "Was not able to merge single reports to combined report csv";
              log.error(message);
              return new OptimizeRuntimeException(message);
            });
  }

  private String[] createCombinedReportHeader(
      final List<SingleReportEvaluationResult<?>> reportResults,
      final ReportResultHeaderMapper singleResultHeaderMapper) {
    return reportResults.stream()
        .map(singleResultHeaderMapper)
        .reduce(ArrayUtils::addAll)
        .map(result -> Arrays.copyOf(result, result.length - 1))
        .orElseThrow(
            () -> new OptimizeRuntimeException("Was not able to create combined report header"));
  }

  interface ReportResultHeaderMapper extends Function<ReportEvaluationResult, String[]> {}
}
