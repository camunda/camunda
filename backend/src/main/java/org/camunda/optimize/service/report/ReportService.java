/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
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
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.engine.importing.BpmnModelUtility.extractProcessDefinitionName;
import static org.camunda.optimize.service.engine.importing.DmnModelUtility.extractDecisionDefinitionName;
import static org.camunda.optimize.service.es.report.command.util.ReportUtil.copyDefinitionMetaDataToUpdate;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportService implements CollectionReferencingService {

  private final ReportWriter reportWriter;
  private final ReportReader reportReader;
  private final AuthorizationCheckReportEvaluationHandler reportEvaluator;
  private final ReportAuthorizationService authorizationService;
  private final ReportRelationService reportRelationService;

  private final CollectionService collectionService;

  public IdDto copyAndMoveReport(String reportId, String userId, String collectionId) {
    return copyAndMoveReport(reportId, userId, collectionId, null);
  }

  public IdDto copyAndMoveReport(String reportId, String userId, String collectionId, String newReportName) {
    if (collectionId != null && !collectionService.existsCollection(collectionId)) {
      throw new NotFoundException("Collection to copy to does not exist!");
    }

    return copyReport(reportId, userId, collectionId, false, newReportName);
  }

  public IdDto copyReport(String reportId, String userId, String newReportName) {
    return copyReport(reportId, userId, null, true, newReportName);
  }

  private IdDto copyReport(final String reportId,
                           final String userId,
                           final String collectionId,
                           final boolean sameCollection,
                           final String newReportName) {
    ReportDefinitionDto oldReportDefinition = reportReader.getReport(reportId);
    final String newName = newReportName != null ? newReportName : oldReportDefinition.getName() + " – Copy";
    final String newCollectionId = sameCollection ? oldReportDefinition.getCollectionId() : collectionId;

    if (!oldReportDefinition.getCombined()) {
      switch (oldReportDefinition.getReportType()) {
        case PROCESS:
          SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
            (SingleProcessReportDefinitionDto) oldReportDefinition;
          return reportWriter.createNewSingleProcessReport(
            userId,
            singleProcessReportDefinitionDto.getData(),
            newName,
            newCollectionId
          );
        case DECISION:
          SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto =
            (SingleDecisionReportDefinitionDto) oldReportDefinition;
          return reportWriter.createNewSingleDecisionReport(
            userId,
            singleDecisionReportDefinitionDto.getData(),
            newName,
            newCollectionId
          );
        default:
          throw new IllegalStateException("Unsupported reportType: " + oldReportDefinition.getReportType());
      }
    } else {
      final String oldCollectionId = oldReportDefinition.getCollectionId();
      CombinedReportDefinitionDto combinedReportDefinition = (CombinedReportDefinitionDto) oldReportDefinition;
      return copyCombinedReport(userId, newName, newCollectionId, oldCollectionId, combinedReportDefinition.getData());
    }
  }

  private IdDto copyCombinedReport(final String userId, final String newName, final String newCollectionId,
                                   final String oldCollectionId, final CombinedReportDataDto oldCombinedReportData) {
    final CombinedReportDataDto newCombinedReportData = new CombinedReportDataDto(
      oldCombinedReportData.getConfiguration(),
      oldCombinedReportData.getVisualization(),
      oldCombinedReportData.getReports()
    );

    if (!StringUtils.equals(newCollectionId, oldCollectionId)) {
      final List<CombinedReportItemDto> newReports = new ArrayList<>();
      oldCombinedReportData.getReports().forEach(combinedReportItemDto -> {
        final IdDto idDto = copyReport(combinedReportItemDto.getId(), userId, null);
        newReports.add(combinedReportItemDto.toBuilder().id(idDto.getId()).build());
      });
      newCombinedReportData.setReports(newReports);
    }

    return reportWriter.createNewCombinedReport(
      userId,
      newCombinedReportData,
      newName,
      newCollectionId
    );
  }

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
      reportWriter.removeSingleReportFromCombinedReports(reportId);
      reportWriter.deleteSingleReport(reportId);
    } else {
      reportWriter.deleteCombinedReport(reportId);
    }

    reportRelationService.handleDeleted(reportDefinition);
  }


  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    if (!reportDefinition.getCombined()) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.findFirstCombinedReportsForSimpleReport(reportDefinition.getId()))
      );
    }
    conflictedItems.addAll(reportRelationService.getConflictedItemsForDeleteReport(reportDefinition));
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
                                                                CombinedReportDefinitionDto updatedReport) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());

    final CombinedProcessReportDefinitionUpdateDto reportUpdate = convertToCombinedProcessReportUpdate(updatedReport);
    final CombinedReportDataDto data = reportUpdate.getData();
    if (data.getReportIds() != null && !data.getReportIds().isEmpty()) {
      final List<SingleProcessReportDefinitionDto> reportsOfCombinedReport = reportReader
        .getAllSingleProcessReportsForIdsOmitXml(data.getReportIds());

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
        log.error(errorMessage);
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

    final SingleProcessReportDefinitionDto currentReportVersion = getSingleProcessReportWithAuthorizationCheck(
      reportId, userId
    );
    final SingleProcessReportDefinitionUpdateDto reportUpdate = convertToSingleProcessReportUpdate(updatedReport);

    if (!force) {
      checkForUpdateConflictsOnSingleProcessDefinition(currentReportVersion, updatedReport);
    }

    reportWriter.updateSingleProcessReport(reportUpdate);
    reportRelationService.handleUpdated(reportId, updatedReport);

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
    reportRelationService.handleUpdated(reportId, updatedReport);
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

    conflictedItems.addAll(
      reportRelationService.getConflictedItemsForUpdatedReport(currentReportVersion, reportUpdateDto)
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private void checkForUpdateConflictsOnSingleDecisionDefinition(SingleDecisionReportDefinitionDto currentReportVersion,
                                                                 SingleDecisionReportDefinitionDto reportUpdateDto) throws
                                                                                                                    OptimizeConflictException {
    final Set<ConflictedItemDto> conflictedItems = reportRelationService.getConflictedItemsForUpdatedReport(
      currentReportVersion,
      reportUpdateDto
    );

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
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getProcessDefinitionKey();
      reportUpdate.getData().setProcessDefinitionName(
        extractProcessDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private SingleDecisionReportDefinitionUpdateDto convertToSingleDecisionReportUpdate(SingleDecisionReportDefinitionDto updatedReport) {
    SingleDecisionReportDefinitionUpdateDto reportUpdate = new SingleDecisionReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDecisionDefinitionKey();
      reportUpdate.getData().setDecisionDefinitionName(
        extractDecisionDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
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
    List<ReportDefinitionDto> reports = reportReader.getAllReportsOmitXml(userId);
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
      return authorizationService.isAuthorizedToSeeProcessReport(
        userId, reportData.getProcessDefinitionKey(), reportData.getTenantIds()
      );
    }
    return true;
  }

  private boolean isAuthorizedToSeeSingleDecisionReport(String userId,
                                                        SingleDecisionReportDefinitionDto reportDefinition) {
    final DecisionReportDataDto reportData = reportDefinition.getData();
    if (reportData != null) {
      return authorizationService.isAuthorizedToSeeDecisionReport(
        userId, reportData.getDecisionDefinitionKey(), reportData.getTenantIds()
      );
    }
    return true;
  }

  private boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto processDefinition = (SingleProcessReportDefinitionDto) reportDefinition;
      final ProcessReportDataDto reportData = processDefinition.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeProcessReport(
          userId, reportData.getProcessDefinitionKey(), reportData.getTenantIds()
        );
      }
    } else if (reportDefinition instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto decisionReport = (SingleDecisionReportDefinitionDto) reportDefinition;
      DecisionReportDataDto reportData = decisionReport.getData();
      if (reportData != null) {
        return authorizationService.isAuthorizedToSeeDecisionReport(
          userId, reportData.getDecisionDefinitionKey(), reportData.getTenantIds()
        );
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

  private Set<ConflictedItemDto> mapCombinedReportsToConflictingItems(List<CombinedReportDefinitionDto> combinedReportDtos) {
    return combinedReportDtos.stream()
      .map(combinedReportDto -> new ConflictedItemDto(
        combinedReportDto.getId(), ConflictedItemType.COMBINED_REPORT, combinedReportDto.getName()
      ))
      .collect(Collectors.toSet());
  }


  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final SimpleCollectionDefinitionDto definition) {
    return reportReader.findReportsForCollection(definition.getId()).stream()
      .map(reportDefinitionDto -> new ConflictedItemDto(
        reportDefinitionDto.getId(), ConflictedItemType.COLLECTION, reportDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final SimpleCollectionDefinitionDto definition) {
    reportWriter.deleteAllReportsOfCollection(definition.getId());
  }
}
