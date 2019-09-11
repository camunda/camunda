/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@AllArgsConstructor
@Component
public class ReportAuthorizationService {

  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final CollectionService collectionService;

  public Optional<RoleType> isAuthorizedToAccessReportByRole(final String userId, final ReportDefinitionDto report) {
    final Optional<RoleType> authorizedByRole = isAuthorizedByRole(userId, report);
    return authorizedByRole.isPresent() && isAuthorizedToAccessDefinition(userId, report)
      ? authorizedByRole
      : Optional.empty();
  }

  private Optional<RoleType> isAuthorizedByRole(final String userId, final ReportDefinitionDto report) {
    RoleType role = null;
    if (report.getCollectionId() != null) {
      role = collectionService.getUsersCollectionResourceRole(report.getCollectionId(), userId).orElse(null);
    } else if (Optional.ofNullable(report.getOwner()).map(owner -> owner.equals(userId)).orElse(true)) {
      role = RoleType.EDITOR;
    }
    return Optional.ofNullable(role);
  }

  private boolean isAuthorizedToAccessDefinition(final String userId,
                                                 final ReportDefinitionDto report) {
    boolean authorizedToAccessDefinition = false;
    if (report instanceof SingleProcessReportDefinitionDto) {
      final ProcessReportDataDto reportData = ((SingleProcessReportDefinitionDto) report).getData();
      if (reportData != null) {
        authorizedToAccessDefinition = definitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
          userId, reportData.getProcessDefinitionKey(), reportData.getTenantIds()
        );
      }
    } else if (report instanceof SingleDecisionReportDefinitionDto) {
      final DecisionReportDataDto reportData = ((SingleDecisionReportDefinitionDto) report).getData();
      if (reportData != null) {
        authorizedToAccessDefinition = definitionAuthorizationService.isAuthorizedToSeeDecisionDefinition(
          userId, reportData.getDecisionDefinitionKey(), reportData.getTenantIds()
        );
      }
    } else if (report instanceof CombinedReportDefinitionDto) {
      // for combined reports the access is handled on evaluation
      authorizedToAccessDefinition = true;
    } else {
      throw new OptimizeRuntimeException("Unsupported report type: " + report.getClass().getSimpleName());
    }
    return authorizedToAccessDefinition;
  }

}
