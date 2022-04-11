/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public interface ReportReferencingService {

  Set<ConflictedItemDto> getConflictedItemsForReportDelete(ReportDefinitionDto reportDefinition);

  void handleReportDeleted(ReportDefinitionDto reportDefinition);

  Set<ConflictedItemDto> getConflictedItemsForReportUpdate(ReportDefinitionDto currentDefinition,
                                                           ReportDefinitionDto updateDefinition);

  void handleReportUpdated(final String reportId, final ReportDefinitionDto updateDefinition);

}