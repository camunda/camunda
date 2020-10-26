/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.process;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.export.CSVUtils;

import javax.validation.constraints.NotNull;
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

@Slf4j
public class CombinedProcessReportResult
  extends ReportEvaluationResult<CombinedProcessReportResultDto<?>, CombinedReportDefinitionRequestDto> {

  public CombinedProcessReportResult(@NotNull final CombinedProcessReportResultDto reportResult,
                                     @NotNull final CombinedReportDefinitionRequestDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    Optional<ResultType> resultType = reportResult.getSingleReportResultType();
    return resultType.map(r -> mapCombinedNumberReportResultsToCsvList(limit, offset, r))
      .orElseGet(() -> {
        log.debug("No reports to evaluate are available in the combined report. Returning empty csv instead.");
        return Collections.singletonList(new String[]{});
      });
  }

  @SuppressWarnings("unchecked")
  private List<String[]> mapCombinedNumberReportResultsToCsvList(final Integer limit, final Integer offset,
                                                                 final ResultType resultType) {
    final List<String[]> csvStrings;
    switch (resultType) {
      case MAP:
        csvStrings = mapCombinedMapReportResultsToCsvList(
          limit,
          offset,
          (CombinedProcessReportResultDto<ReportMapResultDto>) reportResult
        );
        break;
      case NUMBER:
        csvStrings = mapCombinedNumberReportResultsToCsvList(
          1,
          0,
          (CombinedProcessReportResultDto<NumberResultDto>) reportResult
        );
        break;
      default:
        String message = String.format(
          "Unsupported report type [%s] in combined report",
          resultType.getClass().getSimpleName()
        );
        log.error(message);
        throw new OptimizeRuntimeException(message);
    }
    return csvStrings;
  }

  private List<String[]> mapCombinedMapReportResultsToCsvList(final Integer limit, final Integer offset,
                                                              final CombinedProcessReportResultDto<ReportMapResultDto> combinedResult) {

    final List<List<String[]>> allSingleReportsAsCsvList =
      extractListOfReportResultTables(limit, offset, combinedResult);

    final String[] reportNameHeader =
      createCombinedReportHeader(combinedResult, r -> new String[]{r.getReportDefinition().getName(), "", ""});

    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    mergedCsvReports.add(0, reportNameHeader);

    return mergedCsvReports;
  }

  /**
   *
   * @return a list of report result tables where each String[] entry consists of two values - label and value.
   * A List<String[]> corresponds to a report result table with a header, e.g.
   *  flowNodes |  flowNode_frequency
   *  Task A    |  1
   *  Task B    |  50
   */
  private List<List<String[]>> extractListOfReportResultTables(final Integer limit, final Integer offset,
                                                               final CombinedProcessReportResultDto<ReportMapResultDto> combinedResult) {
    final Set<String> allLabels = collectAllLabels(combinedResult);
    final List<List<String[]>> reportResultTable = new ArrayList<>();
    combinedResult.getData().values().forEach(singleResult -> {
      final List<MapResultEntryDto> data = singleResult.getResultAsDto().getData();
      final Set<String> labelsInData = data.stream().map(MapResultEntryDto::getLabel).collect(Collectors.toSet());
      final Sets.SetView<String> newLabels = Sets.difference(allLabels, labelsInData);
      final List<MapResultEntryDto> enrichedData = new ArrayList<>(data);
      newLabels.forEach(newLabel -> enrichedData.add(new MapResultEntryDto(newLabel, null, newLabel)));
      enrichedData.sort(Comparator.comparing(MapResultEntryDto::getLabel, String.CASE_INSENSITIVE_ORDER));
      final List<String[]> singleReportTable = CSVUtils.map(enrichedData, limit, offset);
      new SingleProcessMapReportResult(
        singleResult.getResultAsDto(), singleResult.getReportDefinition()
      ).addCsvHeader(singleReportTable);
      reportResultTable.add(singleReportTable);
    });
    return reportResultTable;
  }

  private Set<String> collectAllLabels(final CombinedProcessReportResultDto<ReportMapResultDto> combinedResult) {
    return combinedResult.getData().values()
      .stream()
      .map(evaluationResult -> new SingleProcessMapReportResult(
        evaluationResult.getResultAsDto(), evaluationResult.getReportDefinition()
      ))
      .map(ReportEvaluationResult::getResultAsDto)
      .flatMap(r -> r.getData().stream())
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toSet());
  }

  interface ReportResultHeaderMapper<T extends SingleReportResultDto>
    extends Function<ReportEvaluationResult<T, SingleProcessReportDefinitionRequestDto>, String[]> {
  }

  private List<String[]> mapCombinedNumberReportResultsToCsvList(
    final Integer limit,
    final Integer offset,
    final CombinedProcessReportResultDto<NumberResultDto> combinedResult) {

    final List<List<String[]>> reportResultTable = combinedResult.getData().values()
      .stream()
      .map(evaluationResult -> new SingleProcessNumberReportResult(
        evaluationResult.getResultAsDto(), evaluationResult.getReportDefinition()
      ))
      .map(reportResult -> reportResult.getResultAsCsv(limit, offset, ZoneId.systemDefault()))
      .collect(Collectors.toList());

    final String[] reportNameHeader =
      createCombinedReportHeader(combinedResult, r -> new String[]{r.getReportDefinition().getName(), ""});

    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(reportResultTable);
    mergedCsvReports.add(0, reportNameHeader);

    return mergedCsvReports;
  }

  private List<String[]> mergeSingleReportsToOneCsv(final List<List<String[]>> allSingleReportsAsCsvList) {
    return allSingleReportsAsCsvList.stream()
      .reduce((l1, l2) -> {
        for (int i = 0; i < l1.size(); i++) {
          String[] firstReportWithSeparatorColumn = ArrayUtils.addAll(l1.get(i), "");
          l1.set(i, ArrayUtils.addAll(firstReportWithSeparatorColumn, l2.get(i)));
        }
        return l1;
      })
      .orElseThrow(() -> {
        String message = "Was not able to merge single reports to combined report csv";
        log.error(message);
        return new OptimizeRuntimeException(message);
      });
  }

  private <T extends SingleReportResultDto> String[] createCombinedReportHeader(
    final CombinedProcessReportResultDto<T> combinedResult,
    final ReportResultHeaderMapper<T> singleResultHeaderMapper) {
    return combinedResult.getData()
      .values()
      .stream()
      .map(singleResultHeaderMapper)
      .reduce(ArrayUtils::addAll)
      .map(result -> Arrays.copyOf(result, result.length - 1))
      .orElseThrow(() -> new OptimizeRuntimeException("Was not able to create combined report header"));
  }

}
