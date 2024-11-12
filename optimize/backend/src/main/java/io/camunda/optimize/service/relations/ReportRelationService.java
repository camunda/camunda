/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.relations;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ReportRelationService {

  private final List<ReportReferencingService> referencingServices;

  @Lazy
  public ReportRelationService(final List<ReportReferencingService> referencingServices) {

    this.referencingServices = referencingServices;
  }

  public Set<ConflictedItemDto> getConflictedItemsForDeleteReport(
      final ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (final ReportReferencingService referencingService : referencingServices) {
      conflictedItems.addAll(
          referencingService.getConflictedItemsForReportDelete(reportDefinition));
    }
    return conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItemsForUpdatedReport(
      final ReportDefinitionDto currentDefinition, final ReportDefinitionDto updateDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (final ReportReferencingService referencingService : referencingServices) {
      conflictedItems.addAll(
          referencingService.getConflictedItemsForReportUpdate(
              currentDefinition, updateDefinition));
    }
    return conflictedItems;
  }

  public void handleDeleted(final ReportDefinitionDto reportDefinition) {
    for (final ReportReferencingService referencingService : referencingServices) {
      referencingService.handleReportDeleted(reportDefinition);
    }
  }

  public void handleUpdated(final String reportId, final ReportDefinitionDto updateDefinition) {
    for (final ReportReferencingService referencingService : referencingServices) {
      referencingService.handleReportUpdated(reportId, updateDefinition);
    }
  }
}
