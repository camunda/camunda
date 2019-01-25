package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


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
    final Integer exportCsvLimit = configurationService.getExportCsvLimit();
    final Integer exportCsvOffset = configurationService.getExportCsvOffset();

    final ReportResultDto reportResultDto = reportService.evaluateSavedReport(userId, reportId);

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
        cast.getData().getView().createCommandKey(),
        exportCsvLimit,
        exportCsvOffset
      ));
    } else if (reportResultDto instanceof RawDataDecisionReportResultDto) {
      RawDataDecisionReportResultDto cast = (RawDataDecisionReportResultDto) reportResultDto;
      result = Optional.of(mapDecisionRawDataToCsvBytes(
        cast.getResult(),
        exportCsvLimit,
        exportCsvOffset,
        excludedColumns
      ));
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
