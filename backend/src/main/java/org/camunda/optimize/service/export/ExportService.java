package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


@Component
public class ExportService {

  @Autowired
  private PlainReportEvaluationHandler reportService;

  @Autowired
  private ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(getClass());

  private byte[] writeRawDataToBytes(
    Map<String, Long> result,
    ProcessGroupByDto groupBy,
    ProcessViewDto view,
    Integer limit,
    Integer offset
  ) {

    List<String[]> csvStrings = CSVUtils.map(result, limit, offset);

    String[] header = new String[2];
    header[0] = groupBy.toString();
    header[1] = view.getOperation() + "_" + view.getEntity() + "_" + view.getProperty();
    csvStrings.add(0, header);

    return getCSVBytes(csvStrings);
  }

  private byte[] writeRawDataToBytes(List<RawDataProcessInstanceDto> rawData, Integer limit, Integer offset) {
    return writeRawDataToBytes(rawData, limit, offset, Collections.emptySet());
  }

  private byte[] writeRawDataToBytes(List<RawDataProcessInstanceDto> rawData,
                                     Integer limit,
                                     Integer offset,
                                     Set<String> excludedColumns) {
    List<String[]> csvStrings = CSVUtils.map(rawData, limit, offset, excludedColumns);

    return getCSVBytes(csvStrings);
  }

  private byte[] getCSVBytes(List<String[]> csvStrings) {
    ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    BufferedWriter bufferedWriter = new BufferedWriter(
      new OutputStreamWriter(arrayOutputStream));
    CSVWriter csvWriter = new CSVWriter(bufferedWriter);
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

  private byte[] getCSVForReport(String userId,
                                 String reportId,
                                 Integer limit,
                                 Integer offset,
                                 Set<String> excludedColumns) {

    Optional<ReportResultDto> reportResultDto;
    reportResultDto = Optional.of(reportService.evaluateSavedReport(userId, reportId));

    byte[] result = reportResultDto.map((reportResult) -> {
      byte[] bytes = null;
      if (reportResult.getClass().equals(RawDataProcessReportResultDto.class)) {
        RawDataProcessReportResultDto cast = (RawDataProcessReportResultDto) reportResult;
        bytes = this.writeRawDataToBytes(
          cast.getResult(),
          limit,
          offset,
          excludedColumns
        );

      } else if (reportResult.getClass().equals(MapProcessReportResultDto.class)) {
        MapProcessReportResultDto cast = (MapProcessReportResultDto) reportResult;
        bytes = this.writeRawDataToBytes(
          cast.getResult(),
          cast.getData().getGroupBy(),
          cast.getData().getView(),
          limit,
          offset
        );
      }
      return bytes;
    }).orElse(null);

    return result;
  }


  public byte[] writeRawDataToBytes(List<RawDataProcessInstanceDto> toMap) {
    return this.writeRawDataToBytes(toMap, null, null);
  }

  public byte[] getCSVForReport(String userId, String reportId, Set<String> excludedColumns) {
    return this.getCSVForReport(
      userId,
      reportId,
      configurationService.getExportCsvLimit(),
      configurationService.getExportCsvOffset(),
      excludedColumns
    );
  }
}
