package org.camunda.optimize.service.es.report.result.process;

import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CombinedProcessReportResult extends ReportResult<CombinedProcessReportResultDto, CombinedReportDataDto> {

  private final static Logger logger = LoggerFactory.getLogger(CombinedProcessReportResult.class);

  public CombinedProcessReportResult(CombinedProcessReportResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final Set<String> excludedColumns) {
    Optional firstResultOptional = reportResultDto.getResult().values().stream().findFirst();
    List<String[]> csvStrings;
    if (firstResultOptional.isPresent()) {
      Object firstResult = firstResultOptional.get();
      if (firstResult instanceof ProcessReportMapResultDto) {
        CombinedProcessReportResultDto<ProcessReportMapResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessReportMapResultDto>) reportResultDto;
        csvStrings = mapCombinedMapResultToCsv(limit, offset, combinedResult);
      } else if (firstResult instanceof ProcessDurationReportMapResultDto) {
        CombinedProcessReportResultDto<ProcessDurationReportMapResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessDurationReportMapResultDto>) reportResultDto;
        csvStrings = mapCombinedDurationMapResultToCsv(limit, offset, combinedResult);
      } else if (firstResult instanceof ProcessReportNumberResultDto) {
        CombinedProcessReportResultDto<ProcessReportNumberResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessReportNumberResultDto>) reportResultDto;
        csvStrings = mapCombinedNumberResultToCsv(combinedResult);
      } else if (firstResult instanceof ProcessDurationReportNumberResultDto) {
        CombinedProcessReportResultDto<ProcessDurationReportNumberResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessDurationReportNumberResultDto>) reportResultDto;
        csvStrings = mapCombinedDurationNumberResultToCsv(combinedResult);
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

  @Override
  public void copyReportData(CombinedReportDataDto data) {
    reportResultDto.setData(data);
  }

  private List<String[]> mapCombinedNumberResultToCsv(CombinedProcessReportResultDto<ProcessReportNumberResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      1, 0, combinedResult, SingleProcessNumberReportResult::new
    );
    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);

    final String[] reportNameHeader = createCombinedReportHeader(combinedResult, r -> new String[]{r.getName(), ""});
    mergedCsvReports.add(0, reportNameHeader);

    return mergedCsvReports;
  }

  private List<String[]> mapCombinedDurationNumberResultToCsv(CombinedProcessReportResultDto<ProcessDurationReportNumberResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      1, 0, combinedResult, SingleProcessNumberDurationReportResult::new
    );
    final List<String[]> csvStrings = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    final String[] reportNameHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getName(), "", "", "", ""}
    );
    csvStrings.add(0, reportNameHeader);
    return csvStrings;
  }

  private List<String[]> mapCombinedMapResultToCsv(Integer limit,
                                                   Integer offset,
                                                   CombinedProcessReportResultDto<ProcessReportMapResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      limit, offset, combinedResult, SingleProcessMapReportResult::new
    );
    final List<String[]> csvStrings = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    final String[] reportNameHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getName(), "", ""}
    );
    csvStrings.add(0, reportNameHeader);
    return csvStrings;
  }

  private List<String[]> mapCombinedDurationMapResultToCsv(Integer limit,
                                                           Integer offset,
                                                           CombinedProcessReportResultDto<ProcessDurationReportMapResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      limit, offset, combinedResult, SingleProcessMapDurationReportResult::new
    );
    final List<String[]> csvStrings = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);

    final String[] combinedReportHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getName(), "", "", "", "", ""}
    );
    csvStrings.add(0, combinedReportHeader);
    return csvStrings;
  }

  private <T extends ProcessReportResultDto> List<List<String[]>> mapCombinedReportResultsToCsvList(
    final Integer limit,
    final Integer offset,
    final CombinedProcessReportResultDto<T> combinedResult,
    final Function<T, ReportResult<?, ?>> reportResultMapper) {

    return combinedResult.getResult().values()
      .stream()
      .map(reportResultMapper::apply)
      .map(reportResult -> reportResult.getResultAsCsv(limit, offset, Collections.emptySet()))
      .collect(Collectors.toList());
  }

  private List<String[]> mergeSingleReportsToOneCsv(final List<List<String[]>> allSingleReportsAsCsvList) {
    int numberOfRows = allSingleReportsAsCsvList.stream().mapToInt(List::size).max().getAsInt();
    return allSingleReportsAsCsvList.stream()
      .reduce((l1, l2) -> {
        fillMissingRowsWithEmptyEntries(numberOfRows, l1);
        fillMissingRowsWithEmptyEntries(numberOfRows, l2);
        for (int i = 0; i < l1.size(); i++) {
          String[] firstReportWithSeparatorColumn = ArrayUtils.addAll(l1.get(i), "");
          l1.set(i, ArrayUtils.addAll(firstReportWithSeparatorColumn, l2.get(i)));
        }
        return l1;
      })
      .orElseThrow(() -> {
        String message = "Was not able to merge single reports to combined report csv";
        logger.error(message);
        return new OptimizeRuntimeException(message);
      });
  }

  private <T extends ProcessReportResultDto> String[] createCombinedReportHeader(final CombinedProcessReportResultDto<T> combinedResult,
                                                                                 final Function<T, String[]> singleResultHeaderMapper) {
    return combinedResult.getResult()
      .values()
      .stream()
      .map(singleResultHeaderMapper)
      .reduce(ArrayUtils::addAll)
      .map(result -> Arrays.copyOf(result, result.length - 1))
      .get();
  }

  private void fillMissingRowsWithEmptyEntries(int numberOfRows, List<String[]> l1) {
    String[] l1Fill = new String[l1.get(0).length];
    Arrays.fill(l1Fill, "");
    IntStream.range(l1.size(), numberOfRows).forEach(i -> l1.add(l1Fill));
  }
}
