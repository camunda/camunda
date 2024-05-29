/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.ReportAuthorizationService;
import io.camunda.optimize.service.variable.ProcessVariableService;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  private final ReportAuthorizationService authorizationService;

  public AuthorizationCheckReportEvaluationHandler(
      final ReportService reportService,
      final SingleReportEvaluator singleReportEvaluator,
      final CombinedReportEvaluator combinedReportEvaluator,
      final ReportAuthorizationService authorizationService,
      final ProcessVariableService processVariableService,
      final DefinitionService definitionService) {
    super(
        reportService,
        singleReportEvaluator,
        combinedReportEvaluator,
        processVariableService,
        definitionService);
    this.authorizationService = authorizationService;
  }

  @Override
  protected Optional<RoleType> getAuthorizedRole(
      final String userId, final ReportDefinitionDto report) {
    return authorizationService.getAuthorizedRole(userId, report);
  }
}
