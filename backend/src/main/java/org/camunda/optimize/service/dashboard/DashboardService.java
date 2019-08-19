/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.dashboard;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.DashboardRelationService;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class DashboardService implements ReportReferencingService, CollectionReferencingService {

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;
  private final DashboardRelationService dashboardRelationService;

  private final ReportService reportService;
  private final CollectionService collectionService;

  public IdDto createNewDashboardAndReturnId(String userId) {
    return dashboardWriter.createNewDashboard(userId);
  }

  public void updateDashboard(DashboardDefinitionDto updatedDashboard, String userId) {
    DashboardDefinitionUpdateDto updateDto = convertToUpdateDto(updatedDashboard, userId);
    dashboardWriter.updateDashboard(updateDto, updatedDashboard.getId());
    dashboardRelationService.handleUpdated(updatedDashboard.getId(), updatedDashboard);
  }

  public IdDto copyDashboard(String dashboardId, String userId, String name) {
    final DashboardDefinitionDto currDashboardDef = Optional.ofNullable(getDashboardDefinition(dashboardId))
      .orElseThrow(() -> new NotFoundException("Dashboard to copy was not found!"));

    String newDashboardName = name != null ? name : currDashboardDef.getName() + " – Copy";
    return dashboardWriter.createNewDashboard(
      userId,
      currDashboardDef.getCollectionId(),
      newDashboardName,
      currDashboardDef.getReports()
    );
  }


  public IdDto copyAndMoveDashboard(String dashboardId, String userId, String collectionId, String name) {
    final DashboardDefinitionDto currDashboardDef = Optional.ofNullable(getDashboardDefinition(dashboardId))
      .orElseThrow(() -> new NotFoundException("Dashboard to copy was not found!"));

    if (collectionId != null && !collectionService.existsCollection(collectionId)) {
      throw new NotFoundException("Collection to copy to does not exist!");
    }

    final List<ReportLocationDto> newDashboardReports = new ArrayList<>(currDashboardDef.getReports());

    if (!isSameCollection(collectionId, currDashboardDef.getCollectionId())) {
      newDashboardReports.clear();
      currDashboardDef.getReports().forEach(reportLocationDto -> {
        final IdDto idDto = reportService.copyAndMoveReport(reportLocationDto.getId(), userId, collectionId);
        newDashboardReports.add(reportLocationDto.toBuilder().id(idDto.getId()).build());
      });
    }

    String newDashboardName = name != null ? name : currDashboardDef.getName() + " – Copy";
    return dashboardWriter.createNewDashboard(
      userId,
      collectionId,
      newDashboardName,
      newDashboardReports
    );
  }

  private boolean isSameCollection(final String newCollectionId, final String oldCollectionId) {
    return StringUtils.equals(newCollectionId, oldCollectionId);
  }


  public List<DashboardDefinitionDto> getDashboardDefinitions() {
    return dashboardReader.getAllDashboards();
  }

  public DashboardDefinitionDto getDashboardDefinition(String dashboardId) {
    return dashboardReader.getDashboard(dashboardId);
  }

  public List<DashboardDefinitionDto> findFirstDashboardsForReport(String reportId) {
    return dashboardReader.findFirstDashboardsForReport(reportId);
  }

  public void removeReportFromDashboards(String reportId) {
    dashboardWriter.removeReportFromDashboards(reportId);
  }

  public void deleteDashboard(String dashboardId, boolean force) throws OptimizeConflictException {
    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDeleteDashboard(dashboardId);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeConflictException(conflictedItems);
      }
    }
    final DashboardDefinitionDto dashboardDefinition = getDashboardDefinition(dashboardId);
    dashboardWriter.deleteDashboard(dashboardId);
    dashboardRelationService.handleDeleted(dashboardDefinition);
  }

  public ConflictResponseDto getDashboardDeleteConflictingItems(String dashboardId) {
    return new ConflictResponseDto(getConflictedItemsForDeleteDashboard(dashboardId));
  }

  private Set<ConflictedItemDto> getConflictedItemsForDeleteDashboard(String dashboardId) {
    return dashboardRelationService.getConflictedItemsForDelete(getDashboardDefinition(dashboardId));
  }

  private DashboardDefinitionUpdateDto convertToUpdateDto(final DashboardDefinitionDto updatedDashboard,
                                                          final String userId) {
    DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setOwner(updatedDashboard.getOwner());
    updateDto.setName(updatedDashboard.getName());
    updateDto.setReports(updatedDashboard.getReports());
    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    return updateDto;
  }


  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(final ReportDefinitionDto reportDefinition) {
    return findFirstDashboardsForReport(reportDefinition.getId()).stream()
      .map(dashboardDefinitionDto -> new ConflictedItemDto(
        dashboardDefinitionDto.getId(), ConflictedItemType.DASHBOARD, dashboardDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    removeReportFromDashboards(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(final ReportDefinitionDto currentDefinition,
                                                                  final ReportDefinitionDto updateDefinition) {
    // NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(final String id, final ReportDefinitionDto updateDefinition) {
    //NOOP
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final SimpleCollectionDefinitionDto definition) {
    return dashboardReader.findDashboardsForCollection(definition.getId()).stream()
      .map(dashboardDefinitionDto -> new ConflictedItemDto(
        dashboardDefinitionDto.getId(), ConflictedItemType.COLLECTION, dashboardDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final SimpleCollectionDefinitionDto definition) {
    dashboardWriter.deleteDashboardsOfCollection(definition.getId());
  }
}
