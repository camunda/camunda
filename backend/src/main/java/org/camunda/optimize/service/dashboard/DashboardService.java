/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.dashboard;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class DashboardService {

  private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

  private DashboardWriter dashboardWriter;
  private DashboardReader dashboardReader;
  private SharingService sharingService;
  private CollectionService collectionService;

  @Autowired
  // to cover for circular dependencies
  @Lazy
  public DashboardService(DashboardWriter dashboardWriter, DashboardReader dashboardReader,
                          SharingService sharingService, CollectionService collectionService) {
    this.dashboardWriter = dashboardWriter;
    this.dashboardReader = dashboardReader;
    this.sharingService = sharingService;
    this.collectionService = collectionService;
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
    sharingService.adjustDashboardShares(updatedDashboard);
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

    dashboardWriter.deleteDashboard(dashboardId);
    collectionService.removeEntityFromCollection(dashboardId);
  }

  public ConflictResponseDto getDashboardDeleteConflictingItems(String dashboardId) {
    return new ConflictResponseDto(getConflictedItemsForDeleteDashboard(dashboardId));
  }

  private Set<ConflictedItemDto> getConflictedItemsForDeleteDashboard(String dashboardId) {
    return new LinkedHashSet<>(
      mapCollectionsToConflictingItems(
        collectionService.findFirstCollectionsForEntity(dashboardId))
    );
  }

  private Set<ConflictedItemDto> mapCollectionsToConflictingItems(List<SimpleCollectionDefinitionDto> collections) {
    return collections.stream()
      .map(collection -> new ConflictedItemDto(
        collection.getId(), ConflictedItemType.COLLECTION, collection.getName()
      ))
      .collect(Collectors.toSet());
  }
}
