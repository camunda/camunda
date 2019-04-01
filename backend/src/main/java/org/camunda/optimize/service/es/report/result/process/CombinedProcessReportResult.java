package org.camunda.optimize.service.es.report.result.process;

import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CombinedProcessReportResult
  extends ReportEvaluationResult<CombinedProcessReportResultDto, CombinedReportDefinitionDto> {

  private final static Logger logger = LoggerFactory.getLogger(CombinedProcessReportResult.class);

  public CombinedProcessReportResult(@NotNull final CombinedProcessReportResultDto reportResult,
                                     @NotNull final CombinedReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final Set<String> excludedColumns) {
    final Optional<ReportEvaluationResult<ProcessReportResultDto, SingleProcessReportDefinitionDto>> firstResultOptional =
      (Optional<ReportEvaluationResult<ProcessReportResultDto, SingleProcessReportDefinitionDto>>)
        reportResult.getData()
          .values()
          .stream()
          .findFirst();
    final List<String[]> csvStrings;
    if (firstResultOptional.isPresent()) {
      final ProcessReportResultDto firstResult = firstResultOptional.get().getResultAsDto();
      if (firstResult instanceof ProcessReportMapResultDto) {
        csvStrings = mapCombinedMapResultToCsv(limit, offset, reportResult);
      } else if (firstResult instanceof ProcessDurationReportMapResultDto) {
        csvStrings = mapCombinedDurationMapResultToCsv(limit, offset, reportResult);
      } else if (firstResult instanceof ProcessReportNumberResultDto) {
        csvStrings = mapCombinedNumberResultToCsv(reportResult);
      } else if (firstResult instanceof ProcessDurationReportNumberResultDto) {
        csvStrings = mapCombinedDurationNumberResultToCsv(reportResult);
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

  private List<String[]> mapCombinedNumberResultToCsv(CombinedProcessReportResultDto<ProcessReportNumberResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      1,
      0,
      combinedResult,
      evaluationResult -> new SingleProcessNumberReportResult(
        evaluationResult.getResultAsDto(), evaluationResult.getReportDefinition()
      )
    );
    final List<String[]> mergedCsvReports = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);

    final String[] reportNameHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getReportDefinition().getName(), ""}
    );
    mergedCsvReports.add(0, reportNameHeader);

    return mergedCsvReports;
  }

  private List<String[]> mapCombinedDurationNumberResultToCsv(CombinedProcessReportResultDto<ProcessDurationReportNumberResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      1,
      0,
      combinedResult,
      evaluationResult -> new SingleProcessNumberDurationReportResult(
        evaluationResult.getResultAsDto(),
        evaluationResult.getReportDefinition()
      )
    );
    final List<String[]> csvStrings = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    final String[] reportNameHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getReportDefinition().getName(), "", "", "", ""}
    );
    csvStrings.add(0, reportNameHeader);
    return csvStrings;
  }

  private List<String[]> mapCombinedMapResultToCsv(Integer limit,
                                                   Integer offset,
                                                   CombinedProcessReportResultDto<ProcessReportMapResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      limit,
      offset,
      combinedResult,
      evaluationResult -> new SingleProcessMapReportResult(
        evaluationResult.getResultAsDto(),
        evaluationResult.getReportDefinition()
      )
    );
    final List<String[]> csvStrings = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);
    final String[] reportNameHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getReportDefinition().getName(), "", ""}
    );
    csvStrings.add(0, reportNameHeader);
    return csvStrings;
  }

  private List<String[]> mapCombinedDurationMapResultToCsv(Integer limit,
                                                           Integer offset,
                                                           CombinedProcessReportResultDto<ProcessDurationReportMapResultDto> combinedResult) {
    final List<List<String[]>> allSingleReportsAsCsvList = mapCombinedReportResultsToCsvList(
      limit,
      offset,
      combinedResult,
      evaluationResult -> new SingleProcessMapDurationReportResult(
        evaluationResult.getResultAsDto(),
        evaluationResult.getReportDefinition()
      )
    );
    final List<String[]> csvStrings = mergeSingleReportsToOneCsv(allSingleReportsAsCsvList);

    final String[] combinedReportHeader = createCombinedReportHeader(
      combinedResult, r -> new String[]{r.getReportDefinition().getName(), "", "", "", "", ""}
    );
    csvStrings.add(0, combinedReportHeader);
    return csvStrings;
  }

  private <T extends ProcessReportResultDto> List<List<String[]>> mapCombinedReportResultsToCsvList(
    final Integer limit,
    final Integer offset,
    final CombinedProcessReportResultDto<T> combinedResult,
    final Function<ReportEvaluationResult<T, SingleProcessReportDefinitionDto>, ReportEvaluationResult<?, ?>> reportResultMapper) {

    return combinedResult.getData().values()
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

  private <T extends ProcessReportResultDto> String[] createCombinedReportHeader(
    final CombinedProcessReportResultDto<T> combinedResult,
    final Function<ReportEvaluationResult<T, SingleProcessReportDefinitionDto>, String[]> singleResultHeaderMapper) {
    return combinedResult.getData()
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
