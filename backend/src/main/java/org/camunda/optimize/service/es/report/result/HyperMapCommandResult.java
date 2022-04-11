/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.result;

import com.google.common.collect.Streams;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.service.export.CSVUtils;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class HyperMapCommandResult extends CommandEvaluationResult<List<HyperMapResultEntryDto>> {

  public HyperMapCommandResult(@NonNull final List<MeasureDto<List<HyperMapResultEntryDto>>> measures,
                               @NonNull final ProcessReportDataDto reportDataDto) {
    super(measures, reportDataDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    return mapHyperMapReportResultsToCsvList(limit, getFirstMeasureData());
  }

  @Override
  public ResultType getType() {
    return ResultType.HYPER_MAP;
  }

  private List<String[]> mapHyperMapReportResultsToCsvList(final Integer limit,
                                                           final List<HyperMapResultEntryDto> hyperMapResult) {

    final List<List<String[]>> allSingleReportsAsCsvList = mapHyperMapReportResultsToCsvLists(limit, hyperMapResult);

    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    addHeaderLine(mergedCsvReports);

    return mergedCsvReports;
  }

  private void addHeaderLine(List<String[]> mergedCsvReports) {
    ProcessReportDataDto data = (ProcessReportDataDto) reportData;
    final String[] reportNameHeader =
      new String[]{data.getDistributedBy().createCommandKey(), data.getGroupBy().createCommandKey()};
    mergedCsvReports.add(0, reportNameHeader);
  }

  private List<List<String[]>> mapHyperMapReportResultsToCsvLists(
    final Integer limit,
    final List<HyperMapResultEntryDto> hyperMapResult) {

    return Streams.mapWithIndex(
      hyperMapResult.stream(),
      (hyperMapResultEntry, index) -> mapSingleHyperMapResultEntry(limit, hyperMapResultEntry, index > 0)
    )
      .collect(Collectors.toList());
  }

  private List<String[]> removeLabelColumn(List<String[]> column) {
    return column.stream()
      .map(row -> row.length > 1 ? ArrayUtils.remove(row, 0) : row)
      .collect(Collectors.toList());
  }

  private List<String[]> mapSingleHyperMapResultEntry(final Integer limit,
                                                      HyperMapResultEntryDto resultEntryDto,
                                                      final boolean removeLabelColumn) {
    List<String[]> csvStrings = CSVUtils.map(resultEntryDto.getValue(), limit, 0);
    final String label =
      resultEntryDto.getLabel();
    final String[] header =
      new String[]{"", label};
    csvStrings.add(0, header);

    if (removeLabelColumn) {
      csvStrings = removeLabelColumn(csvStrings);
    }
    return csvStrings;
  }

  private List<String[]> mergeSingleReportsToOneCsv(final List<List<String[]>> allSingleReportsAsCsvList) {
    int numberOfRows = allSingleReportsAsCsvList.stream().mapToInt(List::size).max().orElse(0);
    return allSingleReportsAsCsvList.stream()
      .reduce((l1, l2) -> {
        fillMissingRowsWithEmptyEntries(numberOfRows, l1);
        fillMissingRowsWithEmptyEntries(numberOfRows, l2);
        for (int i = 0; i < l1.size(); i++) {
          l1.set(i, ArrayUtils.addAll(l1.get(i), l2.get(i)));
        }
        return l1;
      })
      .orElseGet(() -> {
        String message = "Was not able to merge single map entry to hyper map report csv";
        log.warn(message);
        return new ArrayList<>();
      });
  }

  private void fillMissingRowsWithEmptyEntries(int numberOfRows, List<String[]> l1) {
    String[] l1Fill = new String[l1.get(0).length];
    Arrays.fill(l1Fill, "");
    IntStream.range(l1.size(), numberOfRows).forEach(i -> l1.add(l1Fill));
  }

}
