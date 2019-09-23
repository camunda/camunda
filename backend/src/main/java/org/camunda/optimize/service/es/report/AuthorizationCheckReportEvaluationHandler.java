/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthorizationCheckReportEvaluationHandler extends ReportEvaluationHandler {

  private final ReportAuthorizationService authorizationService;

  public AuthorizationCheckReportEvaluationHandler(final ReportReader reportReader,
                                                   final SingleReportEvaluator singleReportEvaluator,
                                                   final CombinedReportEvaluator combinedReportEvaluator,
                                                   final ReportAuthorizationService authorizationService) {
    super(reportReader, singleReportEvaluator, combinedReportEvaluator);
    this.authorizationService = authorizationService;
  }

  @Override
  protected Optional<RoleType> getAuthorizedRole(String userId, ReportDefinitionDto report) {
    return authorizationService.getAuthorizedRole(userId, report);
  }
}
