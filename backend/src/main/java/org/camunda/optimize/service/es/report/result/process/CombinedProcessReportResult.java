package org.camunda.optimize.service.es.report.result.process;

import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.export.CSVUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CombinedProcessReportResult extends ReportResult<CombinedProcessReportResultDto, CombinedReportDataDto> {

  private final static Logger logger = LoggerFactory.getLogger(CombinedProcessReportResult.class);

  public CombinedProcessReportResult(CombinedProcessReportResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    Optional firstResultOptional = reportResultDto.getResult().values().stream().findFirst();
    List<String[]> csvStrings;
    if (firstResultOptional.isPresent()) {
      Object firstResult = firstResultOptional.get();
      if (firstResult instanceof ProcessReportMapResultDto) {
        CombinedProcessReportResultDto<ProcessReportMapResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessReportMapResultDto>) reportResultDto;
        csvStrings = mapCombinedMapResultToCsv(limit, offset, combinedResult);
      } else if (firstResult instanceof ProcessReportNumberResultDto) {
        CombinedProcessReportResultDto<ProcessReportNumberResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessReportNumberResultDto>) reportResultDto;
        csvStrings = mapCombinedNumberResultToCsv(combinedResult);
      } else {
        String message = String.format(
          "Unsupported report type [%s] in combined report",
          firstResult.getClass().getSimpleName()
        );
        logger.error(message);
        throw new RuntimeException(message);
      }
    } else {
      logger.debug("No reports to evaluate are available in the combined report. Returning empty csv instead.");
      csvStrings = Collections.singletonList(new String[]{});
    }
    return csvStrings;
  }

  private List<String[]> mapCombinedNumberResultToCsv(
      CombinedProcessReportResultDto<ProcessReportNumberResultDto> combinedResult) {
    List<List<String[]>> allSingleReportsAsCsvList =
      combinedResult
        .getResult()
        .values()
        .stream()
        .map(r -> {
          List<String[]> list = new LinkedList<>();
          list.add(new String[]{String.valueOf(r.getResult())});
          return list;
        })
        .collect(Collectors.toList());
    List<String[]> csvStrings =
      allSingleReportsAsCsvList.stream().reduce((l1, l2) -> {
        for (int i = 0; i < l1.size(); i++) {
          String[] firstReportWithSeparatorColumn = ArrayUtils.addAll(l1.get(i), "");
          l1.set(i, ArrayUtils.addAll(firstReportWithSeparatorColumn, l2.get(i)));
        }
        return l1;
      })
        .orElseThrow(() -> {
          String message = "Was not able to export combined number report to csv";
          logger.error(message);
          return new OptimizeRuntimeException(message);
        });

    String[] reportNameHeader = combinedResult.getResult()
      .values()
      .stream()
      .map(r -> new String[]{r.getName(), ""})
      .reduce(ArrayUtils::addAll)
      .get();
    String[] columnHeader = combinedResult.getResult()
      .values()
      .stream()
      .map(r -> new String[]{createNormalizedViewString(r), ""})
      .reduce(ArrayUtils::addAll)
      .get();
    csvStrings.add(0, columnHeader);
    csvStrings.add(0, reportNameHeader);
    return csvStrings;
  }

  private String createNormalizedViewString(ProcessReportResultDto r) {
    String viewAsString = r.getData().getView().createCommandKey();
    return viewAsString.replace("-", "_");
  }

  private List<String[]> mapCombinedMapResultToCsv(Integer limit,
                                                   Integer offset,
                                                   CombinedProcessReportResultDto<ProcessReportMapResultDto> combinedResult) {
    List<List<String[]>> allSingleReportsAsCsvList =
      combinedResult
        .getResult()
        .values()
        .stream()
        .map(r -> CSVUtils.map(r.getResult(), limit, offset))
        .collect(Collectors.toList());
    int numberOfRows = allSingleReportsAsCsvList.stream().mapToInt(List::size).max().getAsInt();
    List<String[]> csvStrings =
      allSingleReportsAsCsvList.stream().reduce((l1, l2) -> {
        fillMissingRowsWithEmptyEntries(numberOfRows, l1);
        fillMissingRowsWithEmptyEntries(numberOfRows, l2);
        for (int i = 0; i < l1.size(); i++) {
          String[] firstReportWithSeparatorColumn = ArrayUtils.addAll(l1.get(i), "");
          l1.set(i, ArrayUtils.addAll(firstReportWithSeparatorColumn, l2.get(i)));
        }
        return l1;
      })
        .orElseThrow(() -> {
          String message = "Was not able to export combined map report to csv";
          logger.error(message);
          return new OptimizeRuntimeException(message);
        });

    String[] reportNameHeader = combinedResult.getResult()
      .values()
      .stream()
      .map(r -> new String[]{r.getName(), "", ""})
      .reduce(ArrayUtils::addAll)
      .get();
    String[] columnHeader = combinedResult.getResult()
      .values()
      .stream()
      .map(r -> new String[]{r.getData().getGroupBy().toString(), createNormalizedViewString(r), ""})
      .reduce(ArrayUtils::addAll)
      .get();
    csvStrings.add(0, columnHeader);
    csvStrings.add(0, reportNameHeader);
    return csvStrings;
  }

  private void fillMissingRowsWithEmptyEntries(int numberOfRows, List<String[]> l1) {
    String[] l1Fill = new String[l1.get(0).length];
    Arrays.fill(l1Fill, "");
    IntStream.range(l1.size(), numberOfRows).forEach(i -> l1.add(l1Fill));
  }

  @Override
  public void copyReportData(CombinedReportDataDto data) {
    reportResultDto.setData(data);
  }
}
