/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.report;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.camunda.optimize.service.security.SharingService;
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

import static org.camunda.optimize.service.es.report.command.util.ReportUtil.copyDefinitionMetaDataToUpdate;

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
  private DefinitionAuthorizationService authorizationService;

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
    collectionService.removeEntityFromCollection(reportId);
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
        mapCollectionsToConflictingItems(collectionService.findFirstCollectionsForEntity(reportId))
      );
    } else {
      conflictedItems.addAll(
        mapDashboardsToConflictingItems(dashboardService.findFirstDashboardsForReport(reportId))
      );
      conflictedItems.addAll(
        mapCollectionsToConflictingItems(collectionService.findFirstCollectionsForEntity(reportId))
      );
    }
    return conflictedItems;
  }

  public IdDto createNewSingleDecisionReport(String userId) {
    return reportWriter.createNewSingleDecisionReport(userId);
  }

  public IdDto createNewSingleProcessReport(String userId) {
    return reportWriter.createNewSingleProcessReport(userId);
  }

  public IdDto createNewCombinedProcessReport(String userId) {
    return reportWriter.createNewCombinedReport(userId);
  }

  public void updateCombinedProcessReportWithAuthorizationCheck(String reportId,
                                                                CombinedReportDefinitionDto updatedReport,
                                                                String userId,
                                                                boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());

    final CombinedProcessReportDefinitionUpdateDto reportUpdate = convertToCombinedProcessReportUpdate(updatedReport);
    final CombinedReportDataDto data = reportUpdate.getData();
    if (data.getReportIds() != null && !data.getReportIds().isEmpty()) {
      final List<SingleProcessReportDefinitionDto> reportsOfCombinedReport = reportReader
        .getAllSingleProcessReportsForIds(data.getReportIds());

      final SingleProcessReportDefinitionDto firstReport = reportsOfCombinedReport.get(0);
      final boolean allReportsCanBeCombined = reportsOfCombinedReport.stream()
        .noneMatch(r -> semanticsForCombinedReportChanged(firstReport, r));
      if (allReportsCanBeCombined) {
        final ProcessVisualization visualization = firstReport.getData() == null
          ? null
          : firstReport.getData().getVisualization();
        data.setVisualization(visualization);
      } else {
        final String errorMessage =
          String.format(
            "Can't update combined report with id [%s] and name [%s]. " +
              "The following report ids are not combinable: [%s]",
            reportId, updatedReport.getName(), data.getReportIds()
          );
        logger.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    }
    reportWriter.updateCombinedReport(reportUpdate);
  }

  public void updateSingleProcessReportWithAuthorizationCheck(String reportId,
                                                              SingleProcessReportDefinitionDto updatedReport,
                                                              String userId,
                                                              boolean force) throws OptimizeException {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    SingleProcessReportDefinitionDto currentReportVersion =
      getSingleProcessReportWithAuthorizationCheck(reportId, userId);
    SingleProcessReportDefinitionUpdateDto reportUpdate =
      convertToSingleProcessReportUpdate(updatedReport);

    if (!force) {
      checkForUpdateConflictsOnSingleProcessDefinition(currentReportVersion, updatedReport);
    }

    reportWriter.updateSingleProcessReport(reportUpdate);
    alertService.deleteAlertsIfNeeded(reportId, updatedReport);

    if (semanticsForCombinedReportChanged(currentReportVersion, updatedReport)) {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
    }
  }

  public void updateSingleDecisionReportWithAuthorizationCheck(String reportId,
                                                               SingleDecisionReportDefinitionDto updatedReport,
                                                               String userId,
                                                               boolean force) throws OptimizeConflictException {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    SingleDecisionReportDefinitionDto currentReportVersion =
      getSingleDecisionReportWithAuthorizationCheck(reportId, userId);
    SingleDecisionReportDefinitionUpdateDto reportUpdate =
      convertToSingleDecisionReportUpdate(updatedReport);

    if (!force) {
      checkForUpdateConflictsOnSingleDecisionDefinition(currentReportVersion, updatedReport);
    }

    reportWriter.updateSingleDecisionReport(reportUpdate);
    alertService.deleteAlertsIfNeeded(reportId, updatedReport);
  }

  private void checkForUpdateConflictsOnSingleProcessDefinition(SingleProcessReportDefinitionDto currentReportVersion,
                                                                SingleProcessReportDefinitionDto reportUpdateDto)
    throws OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();

    if (semanticsForCombinedReportChanged(currentReportVersion, reportUpdateDto)) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportId))
      );
    }

    if (alertService.validateIfProcessReportIsSuitableForAlert(currentReportVersion)
      && !alertService.validateIfProcessReportIsSuitableForAlert(reportUpdateDto)) {
      conflictedItems.addAll(mapAlertsToConflictingItems(alertService.findFirstAlertsForReport(reportId)));
    }

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private void checkForUpdateConflictsOnSingleDecisionDefinition(SingleDecisionReportDefinitionDto currentReportVersion,
                                                                 SingleDecisionReportDefinitionDto reportUpdateDto) throws
                                                                                                                    OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();

    if (alertService.validateIfDecisionReportIsSuitableForAlert(currentReportVersion)
      && !alertService.validateIfDecisionReportIsSuitableForAlert(reportUpdateDto)) {
      conflictedItems.addAll(mapAlertsToConflictingItems(alertService.findFirstAlertsForReport(reportId)));
    }

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private boolean semanticsForCombinedReportChanged(SingleProcessReportDefinitionDto firstReport,
                                                    SingleProcessReportDefinitionDto secondReport) {
    boolean result = false;
    if (firstReport.getData() != null) {
      ProcessReportDataDto oldData = firstReport.getData();
      SingleReportDataDto newData = secondReport.getData();
      result = !newData.isCombinable(oldData);
    }
    return result;
  }

  private SingleProcessReportDefinitionUpdateDto convertToSingleProcessReportUpdate(SingleProcessReportDefinitionDto updatedReport) {
    SingleProcessReportDefinitionUpdateDto reportUpdate = new SingleProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate);
    reportUpdate.setData(updatedReport.getData());
    return reportUpdate;
  }

  private SingleDecisionReportDefinitionUpdateDto convertToSingleDecisionReportUpdate(SingleDecisionReportDefinitionDto updatedReport) {
    SingleDecisionReportDefinitionUpdateDto reportUpdate = new SingleDecisionReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate);
    reportUpdate.setData(updatedReport.getData());
    return reportUpdate;
  }

  private CombinedProcessReportDefinitionUpdateDto convertToCombinedProcessReportUpdate(CombinedReportDefinitionDto updatedReport) {
    CombinedProcessReportDefinitionUpdateDto reportUpdate = new CombinedProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate);
    reportUpdate.setData(updatedReport.getData());
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

  private SingleProcessReportDefinitionDto getSingleProcessReportWithAuthorizationCheck(String reportId,
                                                                                        String userId) {
    SingleProcessReportDefinitionDto report = reportReader.getSingleProcessReport(reportId);
    if (!isAuthorizedToSeeSingleProcessReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private SingleDecisionReportDefinitionDto getSingleDecisionReportWithAuthorizationCheck(String reportId,
                                                                                          String userId) {
    SingleDecisionReportDefinitionDto report = reportReader.getSingleDecisionReport(reportId);
    if (!isAuthorizedToSeeSingleDecisionReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  public ReportDefinitionDto getReportWithAuthorizationCheck(String reportId, String userId) {
    ReportDefinitionDto report = reportReader.getReport(reportId);
    if (!isAuthorizedToSeeReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private List<ReportDefinitionDto> filterAuthorizedReports(String userId, List<ReportDefinitionDto> reports) {
    reports = reports
      .stream()
      .filter(report -> isAuthorizedToSeeReport(userId, report))
      .collect(Collectors.toList());
    return reports;
  }

  private boolean isAuthorizedToSeeSingleProcessReport(String userId,
                                                       SingleProcessReportDefinitionDto reportDefinition) {
    final ProcessReportDataDto reportData = reportDefinition.getData();
    if (reportData != null) {
      return
        authorizationService.isAuthorizedToSeeProcessDefinition(userId, reportData.getProcessDefinitionKey());
    }
    return true;
  }

  private boolean isAuthorizedToSeeSingleDecisionReport(String userId,
                                                        SingleDecisionReportDefinitionDto reportDefinition) {
    final DecisionReportDataDto reportData = reportDefinition.getData();
    if (reportData != null) {
      return authorizationService.isAuthorizedToSeeDecisionDefinition(userId, reportData.getDecisionDefinitionKey());
    }
    return true;
  }

  private boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto processDefinition = (SingleProcessReportDefinitionDto) reportDefinition;
      final ProcessReportDataDto reportData = processDefinition.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeProcessDefinition(userId, reportData.getProcessDefinitionKey());
      }
    } else if (reportDefinition instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto decisionReport = (SingleDecisionReportDefinitionDto) reportDefinition;
      DecisionReportDataDto reportData = decisionReport.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeDecisionDefinition(userId, reportData.getDecisionDefinitionKey());
      }
    }
    return true;
  }

  public ReportEvaluationResult evaluateSavedReport(String userId, String reportId) {
    return reportEvaluator.evaluateSavedReport(userId, reportId);
  }

  public ReportEvaluationResult evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    return reportEvaluator.evaluateReport(userId, reportDefinition);
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
