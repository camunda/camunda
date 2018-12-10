package org.camunda.optimize.service.report;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.MultivaluedMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReportService {

  private final static Logger logger = LoggerFactory.getLogger(ReportService.class);

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
  private CollectionService collectionService;

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

    if (!reportDefinition.getCombined()) {
      alertService.deleteAlertsForReport(reportId);
      sharingService.deleteShareForReport(reportId);
      reportWriter.removeSingleReportFromCombinedReports(reportId);
      reportWriter.deleteSingleReport(reportId);
    } else {
      reportWriter.deleteCombinedReport(reportId);
    }
    dashboardService.removeReportFromDashboards(reportId);
    collectionService.removeReportFromCollection(reportId);
  }

  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = reportDefinition.getId();
    if (!reportDefinition.getCombined()) {
      conflictedItems.addAll(
        mapAlertsToConflictingItems(alertService.findFirstAlertsForReport(reportId))
      );
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportId))
      );
      conflictedItems.addAll(
        mapDashboardsToConflictingItems(dashboardService.findFirstDashboardsForReport(reportId))
      );
      conflictedItems.addAll(
        mapCollectionsToConflictingItems(collectionService.findFirstCollectionsForReport(reportId))
      );
    } else {
      conflictedItems.addAll(mapDashboardsToConflictingItems(dashboardService.findFirstDashboardsForReport(reportId)));
    }
    return conflictedItems;
  }

  public IdDto createNewReportAndReturnId(String userId, ReportDefinitionDto<?> reportDefinitionDto) {
    if (reportDefinitionDto instanceof SingleReportDefinitionDto) {
      return reportWriter.createNewSingleReportAndReturnId(userId, (SingleReportDefinitionDto<?>) reportDefinitionDto);
    } else {
      return reportWriter.createNewCombinedReportAndReturnId(userId);
    }
  }

  public void updateCombinedProcessReportWithAuthorizationCheck(String reportId,
                                                                CombinedReportDefinitionDto updatedReport,
                                                                String userId,
                                                                boolean force) throws OptimizeException {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    ReportDefinitionDto currentReportVersion = getReportWithAuthorizationCheck(reportId, userId);
    ReportDefinitionUpdateDto<CombinedReportDataDto> reportUpdate =
      convertToReportUpdate(reportId, updatedReport, userId);

    CombinedReportDataDto data = reportUpdate.getData();
    if (data.getReportIds() != null && !data.getReportIds().isEmpty()) {
      List<SingleReportDefinitionDto<ProcessReportDataDto>> reportsOfCombinedReport =
        reportReader.getAllSingleProcessReportsForIds(data.getReportIds());

      SingleReportDefinitionDto<ProcessReportDataDto> firstReport = reportsOfCombinedReport.get(0);
      boolean allReportsCanBeCombined =
        reportsOfCombinedReport.stream().noneMatch(r -> semanticsForCombinedReportChanged(firstReport, r));
      if (allReportsCanBeCombined) {
        data.setVisualization(firstReport.getData().getVisualization());
      } else {
        String errorMessage =
          String.format("Can't update combined report with id [%s] and name [%s]. " +
                          "The following report ids are not combinable: [%s]",
                        reportId, updatedReport.getName(), data.getReportIds()
          );
        logger.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    }

    if (!force) {
      checkForUpdateConflicts(currentReportVersion, updatedReport);
    }
    reportWriter.updateCombinedReport(reportUpdate);
  }

  public void updateSingleProcessReportWithAuthorizationCheck(String reportId,
                                                              SingleReportDefinitionDto<SingleReportDataDto> updatedReport,
                                                              String userId,
                                                              boolean force) throws OptimizeException {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    ReportDefinitionDto currentReportVersion = getReportWithAuthorizationCheck(reportId, userId);
    ReportDefinitionUpdateDto reportUpdate = convertToReportUpdate(reportId, updatedReport, userId);

    if (!force) {
      checkForUpdateConflicts(currentReportVersion, updatedReport);
    }
    final SingleReportDefinitionDto currentSingleReport = (SingleReportDefinitionDto) currentReportVersion;

    reportWriter.updateSingleReport(reportUpdate);
    alertService.deleteAlertsIfNeeded(reportId, updatedReport);

    if (semanticsForCombinedReportChanged(currentSingleReport, updatedReport)) {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
    }
  }

  private void checkForUpdateConflicts(ReportDefinitionDto currentReportVersion,
                                       ReportDefinitionDto reportUpdateDto) throws OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();
    if (reportUpdateDto instanceof SingleReportDefinitionDto) {
      final SingleReportDefinitionDto currentSingleReport = (SingleReportDefinitionDto) currentReportVersion;
      final SingleReportDefinitionDto singleReportUpdate = (SingleReportDefinitionDto) reportUpdateDto;

      if (semanticsForCombinedReportChanged(currentSingleReport, singleReportUpdate)) {
        conflictedItems.addAll(
          mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportId))
        );
      }

      if (alertService.validateIfReportIsSuitableForAlert(currentSingleReport)
        && !alertService.validateIfReportIsSuitableForAlert(singleReportUpdate)) {
        conflictedItems.addAll(mapAlertsToConflictingItems(alertService.findFirstAlertsForReport(reportId)));
      }
    }

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private boolean semanticsForCombinedReportChanged(SingleReportDefinitionDto oldReportVersion,
                                                    SingleReportDefinitionDto reportUpdate) {
    if (oldReportVersion.getData() instanceof ProcessReportDataDto) {
      ProcessReportDataDto oldData = (ProcessReportDataDto) oldReportVersion.getData();
      SingleReportDataDto newData = reportUpdate.getData();
      return !newData.isCombinable(oldData);
    }
    return false;
  }

  private <T extends ReportDataDto> ReportDefinitionUpdateDto<T> convertToReportUpdate(String reportId,
                                                                                       ReportDefinitionDto<T> updatedReport,
                                                                                       String userId) {
    ReportDefinitionUpdateDto<T> reportUpdate = new ReportDefinitionUpdateDto<>();
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
          if (r instanceof SingleReportDefinitionDto) {
            if (r.getData() instanceof ProcessReportDataDto) {
              ProcessReportDataDto reportData = (ProcessReportDataDto) r.getData();
              return sessionService.isAuthorizedToSeeDefinition(userId, reportData.getProcessDefinitionKey());
            } else {
              return true;
            }
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
    if (reportDefinition instanceof SingleReportDefinitionDto) {
      if (reportDefinition.getData() instanceof ProcessReportDataDto) {
        final ProcessReportDataDto reportData = (ProcessReportDataDto) reportDefinition.getData();
        return sessionService.isAuthorizedToSeeDefinition(userId, reportData.getProcessDefinitionKey());
      } else {
        return true;
      }
    }
    return true;
  }

  public ReportResultDto evaluateSavedReport(String userId, String reportId) {
    return reportEvaluator.evaluateSavedReport(userId, reportId);
  }

  public ReportResultDto evaluateReport(String userId,
                                        ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleReportDefinitionDto) {
      return reportEvaluator.evaluateSingleReport(userId, (SingleReportDefinitionDto) reportDefinition);
    } else {
      return reportEvaluator.evaluateCombinedReport(userId, (CombinedReportDefinitionDto) reportDefinition);
    }
  }

  public ReportResultDto evaluateSingleReport(String userId,
                                                     SingleReportDefinitionDto reportDefinition) {
    return reportEvaluator.evaluateSingleReport(userId, reportDefinition);
  }

  public CombinedProcessReportResultDto evaluateCombinedReport(String userId,
                                                               CombinedReportDefinitionDto reportDefinition) {
    return reportEvaluator.evaluateCombinedReport(userId, reportDefinition);
  }

  private Set<ConflictedItemDto> mapCollectionsToConflictingItems(List<SimpleCollectionDefinitionDto> collections) {
    return collections.stream()
      .map(collection -> new ConflictedItemDto(
        collection.getId(), ConflictedItemType.COLLECTION, collection.getName()
      ))
      .collect(Collectors.toSet());
  }

  private Set<ConflictedItemDto> mapDashboardsToConflictingItems(List<DashboardDefinitionDto> dashboardDtos) {
    return dashboardDtos.stream()
      .map(dashboardDefinitionDto -> new ConflictedItemDto(
        dashboardDefinitionDto.getId(), ConflictedItemType.DASHBOARD, dashboardDefinitionDto.getName()
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
