package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
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
    logger.debug("Exporting report with id [{}] as csv.", reportId);
    final Integer exportCsvLimit = configurationService.getExportCsvLimit();
    final Integer exportCsvOffset = configurationService.getExportCsvOffset();


    final ReportEvaluationResult reportResult;
    try {
      reportResult = reportService.evaluateSavedReport(userId, reportId);
    } catch (Exception e) {
      logger.debug("Could not evaluate report to export the result to csv!", e);
      return Optional.empty();
    }
    List<String[]> resultAsCsv = reportResult.getResultAsCsv(exportCsvLimit, exportCsvOffset, excludedColumns);
    return Optional.of(mapCsvLinesToCsvBytes(resultAsCsv));
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
