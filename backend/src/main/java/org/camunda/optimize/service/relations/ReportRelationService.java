/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ReportRelationService {

  private final List<ReportReferencingService> referencingServices;

  @Lazy
  public ReportRelationService(final List<ReportReferencingService> referencingServices) {

    this.referencingServices = referencingServices;
  }

  public Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (ReportReferencingService referencingService : referencingServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForReportDelete(reportDefinition));
    }
    return conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItemsForUpdatedReport(ReportDefinitionDto currentDefinition,
                                                                   ReportDefinitionDto updateDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (ReportReferencingService referencingService : referencingServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForReportUpdate(currentDefinition, updateDefinition));
    }
    return conflictedItems;
  }

  public void handleDeleted(ReportDefinitionDto reportDefinition) {
    for (ReportReferencingService referencingService : referencingServices) {
      referencingService.handleReportDeleted(reportDefinition);
    }
  }

  public void handleUpdated(final String reportId,
                            final ReportDefinitionDto updateDefinition) {
    for (ReportReferencingService referencingService : referencingServices) {
      referencingService.handleReportUpdated(reportId, updateDefinition);
    }
  }

}
