/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.identity.CollapsedSubprocessNodesService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.variable.ProcessVariableService;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PlainReportEvaluationHandler extends ReportEvaluationHandler {

  public PlainReportEvaluationHandler(
      final ReportService reportService,
      final SingleReportEvaluator singleReportEvaluator,
      final CombinedReportEvaluator combinedReportEvaluator,
      final ProcessVariableService processVariableService,
      final DefinitionService definitionService,
      final CollapsedSubprocessNodesService collapsedSubprocessNodesService) {
    super(
        reportService,
        singleReportEvaluator,
        combinedReportEvaluator,
        processVariableService,
        definitionService,
        collapsedSubprocessNodesService);
  }

  @Override
  protected Optional<RoleType> getAuthorizedRole(
      final String userId, final ReportDefinitionDto report) {
    return Optional.of(RoleType.VIEWER);
  }
}
