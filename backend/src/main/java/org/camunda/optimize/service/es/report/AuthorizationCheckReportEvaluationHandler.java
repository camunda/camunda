/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.camunda.optimize.service.variable.ProcessVariableService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  private final ReportAuthorizationService authorizationService;

  public AuthorizationCheckReportEvaluationHandler(final ReportReader reportReader,
                                                   final SingleReportEvaluator singleReportEvaluator,
                                                   final CombinedReportEvaluator combinedReportEvaluator,
                                                   final ReportAuthorizationService authorizationService,
                                                   final ProcessVariableService processVariableService) {
    super(reportReader, singleReportEvaluator, combinedReportEvaluator, processVariableService);
    this.authorizationService = authorizationService;
  }

  @Override
  protected Optional<RoleType> getAuthorizedRole(final String userId,
                                                 final ReportDefinitionDto report) {
    return authorizationService.getAuthorizedRole(userId, report);
  }
}
