package org.camunda.optimize.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.MultivaluedMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;
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
  private DashboardService dashboardService;

  @Autowired
  private SessionService sessionService;

  public ConflictResponseDto getReportDeleteConflictingItemsWithAuthorizationCheck(String userId, String reportId) {
    ReportDefinitionDto currentReportVersion = getReportWithAuthorizationCheck(reportId, userId);
    return new ConflictResponseDto(getConflictedItemsForDeleteReport(currentReportVersion));
  }

  public void deleteReportWithAuthorizationCheck(String userId, String reportId, boolean force)
    throws OptimizeException {
    final ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    if (!isAuthorizedToSeeReport(userId, reportDefinition)) {
      throw new ForbiddenException(
        "User [" + userId + "] is not authorized to delete report [" + reportDefinition.getName() + "]."
      );
    }

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDeleteReport(reportDefinition);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeConflictException(conflictedItems);
      }
    }

    if (SINGLE_REPORT_TYPE.equals(reportDefinition.getReportType())) {
      alertService.deleteAlertsForReport(reportId);
      sharingService.deleteShareForReport(reportId);
      reportWriter.removeSingleReportFromCombinedReports(reportId);
      dashboardService.removeReportFromDashboards(reportId);
      reportWriter.deleteSingleReport(reportId);
    } else if (COMBINED_REPORT_TYPE.equals(reportDefinition.getReportType())) {
      reportWriter.deleteCombinedReport(reportId);
    }
  }

  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = reportDefinition.getId();
    switch (reportDefinition.getReportType()) {
      case SINGLE_REPORT_TYPE:
        conflictedItems.addAll(
          mapAlertsToConflictingItems(alertService.findFirstAlertsForReport(reportId))
        );
        conflictedItems.addAll(
          mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportId))
        );
        conflictedItems.addAll(
          mapDashboardsToConflictingItems(dashboardService.findFirstDashboardsForReport(reportId))
        );
        break;
      case COMBINED_REPORT_TYPE:
        conflictedItems.addAll(mapDashboardsToConflictingItems(dashboardService.findFirstDashboardsForReport(reportId)));
        break;
      default:
        throw new IllegalStateException("Unsupported report definition type " + reportDefinition.getReportType());
    }
    return conflictedItems;
  }

  public IdDto createNewSingleReportAndReturnId(String userId) {
    return reportWriter.createNewSingleReportAndReturnId(userId);
  }

  public IdDto createNewCombinedReportAndReturnId(String userId) {
    return reportWriter.createNewCombinedReportAndReturnId(userId);
  }

  public void updateReportWithAuthorizationCheck(String reportId,
                                                 ReportDefinitionDto updatedReport,
                                                 String userId,
                                                 boolean force) throws OptimizeException, JsonProcessingException {
    ValidationHelper.validateDefinitionData(updatedReport.getData());
    ReportDefinitionDto currentReportVersion = getReportWithAuthorizationCheck(reportId, userId);
    ReportDefinitionUpdateDto reportUpdate = convertToReportUpdate(reportId, updatedReport, userId);

    if (!force) {
      checkForUpdateConflicts(currentReportVersion, updatedReport);
    }

    if (SINGLE_REPORT_TYPE.equals(updatedReport.getReportType())) {
      reportWriter.updateSingleReport(reportUpdate);
      alertService.deleteAlertsIfNeeded(reportId, updatedReport);

      SingleReportDefinitionDto singleReportUpdate = (SingleReportDefinitionDto) updatedReport;
      if (semanticsForCombinedReportChanged(currentReportVersion, singleReportUpdate)) {
        reportWriter.removeSingleReportFromCombinedReports(reportId);
      }
    } else if (COMBINED_REPORT_TYPE.equals(updatedReport.getReportType())) {
      reportWriter.updateCombinedReport(reportUpdate);
    }

  }

  private void checkForUpdateConflicts(ReportDefinitionDto currentReportVersion,
                                       ReportDefinitionDto reportUpdateDto) throws OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();
    switch (currentReportVersion.getReportType()) {
      case SINGLE_REPORT_TYPE:
        final SingleReportDefinitionDto currentSingleReport = (SingleReportDefinitionDto) currentReportVersion;
        final SingleReportDefinitionDto singleReportUpdate = (SingleReportDefinitionDto) reportUpdateDto;

        if (semanticsForCombinedReportChanged(currentReportVersion, singleReportUpdate)) {
          conflictedItems.addAll(
            mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportId))
          );
        }

        if (alertService.validateIfReportIsSuitableForAlert(currentSingleReport)
          && !alertService.validateIfReportIsSuitableForAlert(singleReportUpdate)) {
          conflictedItems.addAll(mapAlertsToConflictingItems(alertService.findFirstAlertsForReport(reportId)));
        }
        break;
      case COMBINED_REPORT_TYPE:
        // noop
        break;
      default:
        throw new IllegalStateException("Unsupported report definition type " + currentReportVersion.getReportType());
    }

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private boolean semanticsForCombinedReportChanged(ReportDefinitionDto oldReportVersion,
                                                    SingleReportDefinitionDto reportUpdate) {
    if (SINGLE_REPORT_TYPE.equals(oldReportVersion.getReportType())) {
      SingleReportDefinitionDto oldSingleReport = (SingleReportDefinitionDto) oldReportVersion;
      SingleReportDataDto oldData = oldSingleReport.getData();
      SingleReportDataDto newData = reportUpdate.getData();
      return !newData.isCombinable(oldData);
    }
    return false;
  }

  private ReportDefinitionUpdateDto convertToReportUpdate(String reportId,
                                                          ReportDefinitionDto updatedReport,
                                                          String userId) {
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

  public CombinedReportResultDto<?> evaluateCombinedReport(String userId,
                                                           CombinedReportDefinitionDto reportDefinition) {
    return reportEvaluator.evaluateCombinedReport(userId, reportDefinition);
  }

  private Set<ConflictedItemDto> mapDashboardsToConflictingItems(List<DashboardDefinitionDto> dashboardDtos) {
    return dashboardDtos.stream()
      .map(alertDefinitionDto -> new ConflictedItemDto(
        alertDefinitionDto.getId(), ConflictedItemType.DASHBOARD, alertDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  private Set<ConflictedItemDto> mapCombinedReportsToConflictingItems(List<CombinedReportDefinitionDto> combinedReportDtos) {
    return combinedReportDtos.stream()
      .map(combinedReportDto -> new ConflictedItemDto(
        combinedReportDto.getId(), ConflictedItemType.COMBINED_REPORT, combinedReportDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  private Set<ConflictedItemDto> mapAlertsToConflictingItems(List<AlertDefinitionDto> alertsForReport) {
    return alertsForReport.stream()
      .map(alertDto -> new ConflictedItemDto(alertDto.getId(), ConflictedItemType.ALERT, alertDto.getName()))
      .collect(Collectors.toSet());
  }

}
