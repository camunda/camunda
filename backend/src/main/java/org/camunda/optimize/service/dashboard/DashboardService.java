/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.dashboard;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.relations.DashboardRelationService;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class DashboardService implements ReportReferencingService {

  private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

  private DashboardWriter dashboardWriter;
  private DashboardReader dashboardReader;

  private DashboardRelationService dashboardRelationService;

  @Autowired
  public DashboardService(DashboardWriter dashboardWriter,
                          DashboardReader dashboardReader,
                          DashboardRelationService dashboardRelationService) {
    this.dashboardWriter = dashboardWriter;
    this.dashboardReader = dashboardReader;
    this.dashboardRelationService = dashboardRelationService;
  }

  public IdDto createNewDashboardAndReturnId(String userId) {
    return dashboardWriter.createNewDashboardAndReturnId(userId);
  }

  public void updateDashboard(DashboardDefinitionDto updatedDashboard, String userId) {
    DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    updateDto.setOwner(updatedDashboard.getOwner());
    updateDto.setName(updatedDashboard.getName());
    updateDto.setReports(updatedDashboard.getReports());
    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    dashboardWriter.updateDashboard(updateDto, updatedDashboard.getId());
    dashboardRelationService.handleUpdated(updatedDashboard.getId(), updatedDashboard);
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
}
