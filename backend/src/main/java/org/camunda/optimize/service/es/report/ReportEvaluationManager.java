package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReportEvaluationManager {

  @Autowired
  private RawDataReportEvaluator rawDataReportEvaluator;

  public static final String RAW_DATA_OPERATION = "rawData";


  public ReportResultDto evaluate(ReportDataDto reportData) throws IOException {
    ValidationHelper.validate(reportData);
    ViewDto view = reportData.getView();
    String operation = view.getOperation();
    switch (operation) {
      case RAW_DATA_OPERATION:
        return rawDataReportEvaluator.evaluate(reportData);
      default:
        return rawDataReportEvaluator.evaluate(reportData);
    }
  }


}
