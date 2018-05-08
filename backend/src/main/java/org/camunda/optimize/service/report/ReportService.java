package org.camunda.optimize.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.ReportEvaluator;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

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

  @Autowired
  private SessionService sessionService;

  public void deleteReport(String userId, String reportId) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    ReportDataDto reportData = reportDefinition.getData();
    if (reportData != null && !isAuthorizedToPerformActionOnReport(userId, reportData)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to delete report " +
        "for process definition [" + reportData.getProcessDefinitionKey() + "].");
    }

    alertService.deleteAlertsForReport(reportId);
    sharingService.deleteShareForReport(reportId);
    reportWriter.deleteReport(reportId);
  }

  public IdDto createNewReportAndReturnId(String userId) {
    return reportWriter.createNewReportAndReturnId(userId);
  }

  public void updateReport(String reportId, ReportDefinitionDto updatedReport, String userId) throws OptimizeException, JsonProcessingException {
    ValidationHelper.validateDefinition(updatedReport.getData());
    ReportDefinitionUpdateDto reportUpdate = convertToReportUpdate(reportId, updatedReport, userId);
    reportWriter.updateReport(reportUpdate);
    alertService.deleteAlertsIfNeeded(reportId, updatedReport.getData());
  }

 private ReportDefinitionUpdateDto convertToReportUpdate(String reportId, ReportDefinitionDto updatedReport, String userId) {
    ReportDefinitionUpdateDto reportUpdate = new ReportDefinitionUpdateDto();
    reportUpdate.setData(updatedReport.getData());
    reportUpdate.setId(updatedReport.getId());
    reportUpdate.setLastModified(updatedReport.getLastModified());
    reportUpdate.setLastModifier(updatedReport.getLastModifier());
    reportUpdate.setName(updatedReport.getName());
    reportUpdate.setOwner(updatedReport.getOwner());
    reportUpdate.setId(reportId);
    reportUpdate.setLastModifier(userId);
    reportUpdate.setLastModified(LocalDateUtil.getCurrentDateTime());
    return reportUpdate;
  }

  public List<ReportDefinitionDto> findAndFilterReports(String userId,
                                                        MultivaluedMap<String, String> queryParameters) throws IOException {
    List<ReportDefinitionDto> reports = reportReader.getAllReports();
    reports = filterAuthorizedReports(userId, reports);
    reports = QueryParamAdjustmentUtil.adjustReportResultsToQueryParameters(reports, queryParameters);
    return reports;
  }

  private List<ReportDefinitionDto> filterAuthorizedReports(String userId, List<ReportDefinitionDto> reports) {
    reports = reports
      .stream()
      .filter(
        r -> r.getData() == null ||
          sessionService
          .isAuthorizedToSeeDefinition(userId, r.getData().getProcessDefinitionKey()))
      .collect(Collectors.toList());
    return reports;
  }

  public ReportDefinitionDto getReport(String reportId) {
    return reportReader.getReport(reportId);
  }

  public ReportResultDto evaluateSavedReportWithAuthorizationCheck(String userId, String reportId) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    ReportResultDto result = evaluateReportWithAuthorizationCheck(userId, reportDefinition);
    ReportUtil.copyMetaData(reportDefinition, result);
    return result;
  }

  public ReportResultDto evaluateReportWithAuthorizationCheck(String userId,
                                                              ReportDefinitionDto reportDefinition) {
    ReportDataDto reportData = reportDefinition.getData();
    if (reportData != null && !isAuthorizedToPerformActionOnReport(userId, reportData)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to evaluate report " +
        "for process definition [" + reportData.getProcessDefinitionKey() + "].");
    }
    return evaluateWithErrorCheck(reportDefinition);
  }

  private ReportResultDto evaluateWithErrorCheck(ReportDefinitionDto reportDefinition) throws ReportEvaluationException {
    ReportDataDto reportData = reportDefinition.getData();
    try {
      return reportEvaluator.evaluate(reportData);
    } catch (OptimizeException | OptimizeValidationException e) {
      ReportDefinitionDto definitionWrapper = new ReportDefinitionDto();
      definitionWrapper.setData(reportData);
      definitionWrapper.setName(reportDefinition.getName());
      definitionWrapper.setId(reportDefinition.getId());
      throw new ReportEvaluationException(definitionWrapper, e);
    }
  }

  public ReportResultDto evaluateSavedReport(String reportId) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    ReportResultDto result = evaluateWithErrorCheck(reportDefinition);
    ReportUtil.copyMetaData(reportDefinition, result);
    return result;
  }

  private boolean isAuthorizedToPerformActionOnReport(String userId, ReportDataDto reportDataDto) {
    return sessionService.isAuthorizedToSeeDefinition(userId, reportDataDto.getProcessDefinitionKey());
  }
}
