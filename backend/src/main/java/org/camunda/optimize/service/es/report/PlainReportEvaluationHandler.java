package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.springframework.stereotype.Component;

@Component
public class PlainReportEvaluationHandler extends ReportEvaluationHandler {


  public PlainReportEvaluationHandler(ReportReader reportReader, SingleReportEvaluator singleReportEvaluator,
                                      CombinedReportEvaluator combinedReportEvaluator) {
    super(reportReader, singleReportEvaluator, combinedReportEvaluator);
  }

  public ReportResult evaluateSavedReport(String reportId) {
    return this.evaluateSavedReport(null, reportId);
  }

  public ReportResult evaluateReport(ReportDefinitionDto reportDefinition) {
    return this.evaluateReport(null, reportDefinition);
  }

  @Override
  protected boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report) {
    return true;
  }
}
