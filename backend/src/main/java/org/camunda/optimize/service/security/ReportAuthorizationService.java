/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@AllArgsConstructor
@Component
public class ReportAuthorizationService {

  private final IdentityService identityService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final AuthorizedCollectionService collectionAuthorizationService;

  public Optional<RoleType> getAuthorizedRole(final String userId, final ReportDefinitionDto report) {
    final boolean isSuperUser = identityService.isSuperUserIdentity(userId);
    final Optional<RoleType> authorizedRole = isSuperUser
      ? Optional.of(RoleType.EDITOR)
      : getAuthorizedReportRole(userId, report);
    return authorizedRole.filter(role -> isAuthorizedToAccessReportDefinition(userId, IdentityType.USER, report));
  }

  private Optional<RoleType> getAuthorizedReportRole(final String userId, final ReportDefinitionDto report) {
    RoleType role = null;
    if (report.getCollectionId() != null) {
      role = collectionAuthorizationService.getUsersCollectionResourceRole(userId, report.getCollectionId())
        .orElse(null);
    } else if (Optional.ofNullable(report.getOwner()).map(owner -> owner.equals(userId)).orElse(true)) {
      role = RoleType.EDITOR;
    }
    return Optional.ofNullable(role);
  }

  public boolean isAuthorizedToAccessReportDefinition(final String identityId,
                                                      final IdentityType identityType,
                                                      final ReportDefinitionDto report) {
    boolean authorizedToAccessDefinition = false;
    if (report instanceof SingleProcessReportDefinitionDto) {
      final ProcessReportDataDto reportData = ((SingleProcessReportDefinitionDto) report).getData();
      if (reportData != null) {
        authorizedToAccessDefinition = definitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
          identityId, identityType, reportData.getProcessDefinitionKey(), reportData.getTenantIds()
        );
      }
    } else if (report instanceof SingleDecisionReportDefinitionDto) {
      final DecisionReportDataDto reportData = ((SingleDecisionReportDefinitionDto) report).getData();
      if (reportData != null) {
        authorizedToAccessDefinition = definitionAuthorizationService.isUserAuthorizedToSeeDecisionDefinition(
          identityId, identityType, reportData.getDecisionDefinitionKey(), reportData.getTenantIds()
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
