package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Component
public class ExportService {

  private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

  private final AuthorizationCheckReportEvaluationHandler reportService;
  private final ConfigurationService configurationService;

  @Autowired
  public ExportService(final AuthorizationCheckReportEvaluationHandler reportService,
                       final ConfigurationService configurationService) {
    this.reportService = reportService;
    this.configurationService = configurationService;
  }

  public Optional<byte[]> getCsvBytesForEvaluatedReportResult(final String userId, String reportId,
                                                              final Set<String> excludedColumns) {
    logger.debug("Exporting report with id [{}] as csv.", reportId);
    final Integer exportCsvLimit = configurationService.getExportCsvLimit();
    final Integer exportCsvOffset = configurationService.getExportCsvOffset();


    final ReportResultDto reportResultDto;
    try {
      reportResultDto = reportService.evaluateSavedReport(userId, reportId);
    } catch (Exception e) {
      logger.debug("Could not evaluate report to export the result to csv!", e);
      return Optional.empty();
    }

    final Optional<byte[]> result;
    if (reportResultDto instanceof RawDataProcessReportResultDto) {
      RawDataProcessReportResultDto cast = (RawDataProcessReportResultDto) reportResultDto;
      result = Optional.of(mapProcessRawDataToCsvBytes(
        cast.getResult(),
        exportCsvLimit,
        exportCsvOffset,
        excludedColumns
      ));
    } else if (reportResultDto instanceof ProcessReportMapResultDto) {
      ProcessReportMapResultDto cast = (ProcessReportMapResultDto) reportResultDto;
      result = Optional.of(mapMapResultToCsvBytes(
        cast.getResult(),
        cast.getData().getGroupBy().toString(),
        createNormalizedViewString(cast),
        exportCsvLimit,
        exportCsvOffset
      ));
    } else if (reportResultDto instanceof DecisionReportMapResultDto) {
      DecisionReportMapResultDto cast = (DecisionReportMapResultDto) reportResultDto;
      result = Optional.of(mapMapResultToCsvBytes(
        cast.getResult(),
        cast.getData().getGroupBy().toString(),
        cast.getData().getView().createCommandKey(),
        exportCsvLimit,
        exportCsvOffset
      ));
    } else if (reportResultDto instanceof ProcessReportNumberResultDto) {
      ProcessReportNumberResultDto cast = (ProcessReportNumberResultDto) reportResultDto;
      result = Optional.of(mapNumberResultToCsvBytes(
        cast.getResult(),
        cast.getData().getView().createCommandKey()
      ));
    } else if (reportResultDto instanceof DecisionReportNumberResultDto) {
      DecisionReportNumberResultDto cast = (DecisionReportNumberResultDto) reportResultDto;
      result = Optional.of(mapNumberResultToCsvBytes(
        cast.getResult(),
        cast.getData().getView().createCommandKey()
      ));
    } else if (reportResultDto instanceof RawDataDecisionReportResultDto) {
      RawDataDecisionReportResultDto cast = (RawDataDecisionReportResultDto) reportResultDto;
      result = Optional.of(mapDecisionRawDataToCsvBytes(
        cast.getResult(),
        exportCsvLimit,
        exportCsvOffset,
        excludedColumns
      ));
    } else if (reportResultDto instanceof CombinedProcessReportResultDto) {
      CombinedProcessReportResultDto combinedReportResult = (CombinedProcessReportResultDto) reportResultDto;
      result = Optional.of(
        mapCombinedResultToCsvBytes(combinedReportResult, exportCsvLimit, exportCsvOffset)
      );
    } else {
      logger.warn("CSV export called on unsupported report type {}", reportResultDto.getClass().getSimpleName());
      result = Optional.empty();
    }

    return result;
  }

  private byte[] mapProcessRawDataToCsvBytes(final List<RawDataProcessInstanceDto> rawData,
                                             final Integer limit,
                                             final Integer offset,
                                             final Set<String> excludedColumns) {
    final List<String[]> csvStrings = CSVUtils.mapRawProcessReportInstances(rawData, limit, offset, excludedColumns);
    return mapCsvLinesToCsvBytes(csvStrings);
  }

  private byte[] mapDecisionRawDataToCsvBytes(final List<RawDataDecisionInstanceDto> rawData,
                                              final Integer limit,
                                              final Integer offset,
                                              final Set<String> excludedColumns) {
    final List<String[]> csvStrings = CSVUtils.mapRawDecisionReportInstances(rawData, limit, offset, excludedColumns);
    return mapCsvLinesToCsvBytes(csvStrings);
  }

  private byte[] mapMapResultToCsvBytes(final Map<String, Long> result,
                                        final String groupByString,
                                        final String commandKey,
                                        final Integer limit,
                                        final Integer offset) {
    final List<String[]> csvStrings = CSVUtils.map(result, limit, offset);

    final String normalizedCommandKey = commandKey.replace("-", "_");
    final String[] header = new String[]{groupByString, normalizedCommandKey};
    csvStrings.add(0, header);

    return mapCsvLinesToCsvBytes(csvStrings);
  }

  private byte[] mapCombinedResultToCsvBytes(final CombinedProcessReportResultDto result,
                                             final Integer limit,
                                             final Integer offset) {
    Optional firstResultOptional = result.getResult().values().stream().findFirst();
    List<String[]> csvStrings;
    if (firstResultOptional.isPresent()) {
      Object firstResult = firstResultOptional.get();
      if (firstResult instanceof ProcessReportMapResultDto) {
        CombinedProcessReportResultDto<ProcessReportMapResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessReportMapResultDto>) result;
        csvStrings = mapCombinedMapResultToCsv(limit, offset, combinedResult);
      } else if (firstResult instanceof ProcessReportNumberResultDto) {
        CombinedProcessReportResultDto<ProcessReportNumberResultDto> combinedResult =
          (CombinedProcessReportResultDto<ProcessReportNumberResultDto>) result;
        csvStrings = mapCombinedNumberResultToCsv(combinedResult);
      } else {
        String message = String.format("Unsupported report type [%s] in combined report", firstResult.getClass().getSimpleName());
        logger.error(message);
        throw new RuntimeException(message);
      }
    } else {
      logger.debug("No reports to evaluate are available in the combined report. Returning empty csv instead.");
      csvStrings = Collections.singletonList(new String[]{});
    }
    return mapCsvLinesToCsvBytes(csvStrings);
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

  private String createNormalizedViewString(ProcessReportResultDto r) {
    String viewAsString = r.getData().getView().createCommandKey();
    return viewAsString.replace("-", "_");
  }

  private void fillMissingRowsWithEmptyEntries(int numberOfRows, List<String[]> l1) {
    String[] l1Fill = new String[l1.get(0).length];
    Arrays.fill(l1Fill, "");
    IntStream.range(l1.size(), numberOfRows).forEach(i -> l1.add(l1Fill));
  }

  private byte[] mapNumberResultToCsvBytes(final Long numberResult, final String commandKey) {
    final List<String[]> csvStrings = new LinkedList<>();
    csvStrings.add(new String[]{numberResult.toString()});

    final String normalizedCommandKey = commandKey.replace("-", "_");
    final String[] header = new String[]{normalizedCommandKey};
    csvStrings.add(0, header);

    return mapCsvLinesToCsvBytes(csvStrings);
  }

  private byte[] mapCsvLinesToCsvBytes(final List<String[]> csvStrings) {
    final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    final BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(arrayOutputStream));
    final CSVWriter csvWriter = new CSVWriter(bufferedWriter);

    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (Exception e) {
      logger.error("can't write CSV to buffer", e);
    }
    return bytes;
  }

}
