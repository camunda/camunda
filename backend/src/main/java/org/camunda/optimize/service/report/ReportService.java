package org.camunda.optimize.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.ReportEvaluator;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class ReportService {

  @Autowired
  private ReportWriter reportWriter;

  @Autowired
  private ReportReader reportReader;

  @Autowired
  private ReportEvaluator reportEvaluator;

  @Autowired
  private AlertService alertService;

  @Autowired
  private SharingService sharingService;

  public void deleteReport(String reportId) {
    alertService.deleteAlertsForReport(reportId);
    sharingService.deleteShareForReport(reportId);
    reportWriter.deleteReport(reportId);
  }

  public IdDto createNewReportAndReturnId(String userId) {
    return reportWriter.createNewReportAndReturnId(userId);
  }

  public void updateReport(String reportId, ReportDefinitionDto updatedReport, String userId) throws OptimizeException, JsonProcessingException {
    updatedReport.setId(reportId);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    ValidationHelper.validateDefinition(updatedReport.getData());
    reportWriter.updateReport(updatedReport);
    alertService.deleteAlertsIfNeeded(reportId, updatedReport.getData());
  }

  public List<ReportDefinitionDto> findAndFilterReports(MultivaluedMap<String, String> queryParameters) throws IOException {
    List<ReportDefinitionDto> reports = reportReader.getAllReports();
    reports = QueryParamAdjustmentUtil.adjustReportResultsToQueryParameters(reports, queryParameters);
    return reports;
  }

  public ReportDefinitionDto getReport(String reportId) throws OptimizeException {
    return reportReader.getReport(reportId);
  }

  public ReportResultDto evaluateSavedReport(String reportId) throws OptimizeException {
    ReportDefinitionDto reportDefinition;
    reportDefinition = reportReader.getReport(reportId);
    ReportResultDto result;
    try {
      result = reportEvaluator.evaluate(reportDefinition);
    } catch (OptimizeException e) {
      throw new ReportEvaluationException(reportDefinition, e);
    } catch (OptimizeValidationException e) {
      throw new ReportEvaluationException(reportDefinition, e);
    }
    return result;
  }

  public ReportResultDto evaluateReportInMemory(ReportDataDto reportData) throws OptimizeException {
    ReportResultDto result;
    try {
      result = reportEvaluator.evaluate(reportData);
    } catch (OptimizeException e) {
      ReportDefinitionDto definitionWrapper = new ReportDefinitionDto();
      definitionWrapper.setData(reportData);
      throw new ReportEvaluationException(definitionWrapper, e);
    }
    return result;
  }
}
