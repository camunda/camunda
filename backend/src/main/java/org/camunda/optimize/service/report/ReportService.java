package org.camunda.optimize.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedMapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;

@Component
public class ReportService {

  @Autowired
  private ReportWriter reportWriter;

  @Autowired
  private ReportReader reportReader;

  @Autowired
  private AuthorizationCheckReportEvaluationHandler reportEvaluator;

  @Autowired
  private AlertService alertService;

  @Autowired
  private SharingService sharingService;

  @Autowired
  private SessionService sessionService;

  public void deleteReportWithAuthorizationCheck(String userId, String reportId) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    if (!isAuthorizedToSeeReport(userId, reportDefinition)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to delete report [" +
        reportDefinition.getName() + "].");
    }

    alertService.deleteAlertsForReport(reportId);
    sharingService.deleteShareForReport(reportId);
    reportWriter.deleteReport(reportId);
  }

  public IdDto createNewReportAndReturnId(String userId, String reportType) {
    return reportWriter.createNewReportAndReturnId(userId, reportType);
  }

  public void updateReportWithAuthorizationCheck(String reportId,
                                                 ReportDefinitionDto updatedReport,
                                                 String userId) throws OptimizeException, JsonProcessingException {
    ValidationHelper.validateDefinitionData(updatedReport.getData());
    getReportWithAuthorizationCheck(reportId, userId);
    ReportDefinitionUpdateDto reportUpdate = convertToReportUpdate(reportId, updatedReport, userId);
    reportWriter.updateReport(reportUpdate);
    alertService.deleteAlertsIfNeeded(reportId, updatedReport);
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
                                                        MultivaluedMap<String, String> queryParameters) {
    List<ReportDefinitionDto> reports = findAndFilterReports(userId);
    reports = QueryParamAdjustmentUtil.adjustReportResultsToQueryParameters(reports, queryParameters);
    return reports;
  }

  public List<ReportDefinitionDto> findAndFilterReports(String userId) {
    List<ReportDefinitionDto> reports = reportReader.getAllReports();
    reports = filterAuthorizedReports(userId, reports);
    return reports;
  }

  private List<ReportDefinitionDto> filterAuthorizedReports(String userId, List<ReportDefinitionDto> reports) {
    reports = reports
      .stream()
      .filter(
        r -> {
          if (SINGLE_REPORT_TYPE.equals(r.getReportType())) {
            SingleReportDefinitionDto report = (SingleReportDefinitionDto) r;
            return r.getData() == null ||
              sessionService
                .isAuthorizedToSeeDefinition(userId, report.getData().getProcessDefinitionKey());
          } else {
            return true;
          }
        })
      .collect(Collectors.toList());
    return reports;
  }

  public ReportDefinitionDto getReportWithAuthorizationCheck(String reportId, String userId) {
    ReportDefinitionDto report = reportReader.getReport(reportId);
    if (!isAuthorizedToSeeReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
        report.getName() + "].");
    }
    return report;
  }

  private boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto reportDefinition) {
    if (SINGLE_REPORT_TYPE.equals(reportDefinition.getReportType())) {
      SingleReportDefinitionDto singleReport = (SingleReportDefinitionDto) reportDefinition;
      SingleReportDataDto reportData = singleReport.getData();
      return
        reportData == null || sessionService.isAuthorizedToSeeDefinition(userId, reportData.getProcessDefinitionKey());
    }
    return true;
  }

  public ReportResultDto evaluateSavedReport(String userId, String reportId) {
    return reportEvaluator.evaluateSavedReport(userId, reportId);
  }

  public SingleReportResultDto evaluateSingleReport(String userId,
                                                    SingleReportDefinitionDto reportDefinition) {
    return reportEvaluator.evaluateSingleReport(userId, reportDefinition);
  }

  public CombinedMapReportResultDto evaluateCombinedReport(String userId,
                                                    CombinedReportDefinitionDto reportDefinition) {
    return reportEvaluator.evaluateCombinedReport(userId, reportDefinition);
  }

}
