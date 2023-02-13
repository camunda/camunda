/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@AllArgsConstructor
@Component
public class ReportAuthorizationService {

  private final AbstractIdentityService identityService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final AuthorizedCollectionService collectionAuthorizationService;
  private final ReportReader reportReader;

  public boolean isAuthorizedToReport(final String userId, final ReportDefinitionDto<?> report) {
    return getAuthorizedRole(userId, report).isPresent();
  }

  public Optional<RoleType> getAuthorizedRole(final String userId, final ReportDefinitionDto<?> report) {
    final boolean isSuperUser = identityService.isSuperUserIdentity(userId);
    final Optional<RoleType> authorizedRole = isSuperUser
      ? Optional.of(RoleType.EDITOR)
      : getAuthorizedReportRole(userId, report);
    return authorizedRole.filter(role -> isAuthorizedToAccessReportDefinition(userId, report));
  }

  private Optional<RoleType> getAuthorizedReportRole(final String userId, final ReportDefinitionDto<?> report) {
    RoleType role = null;
    if (report.getCollectionId() != null) {
      role = collectionAuthorizationService.getUsersCollectionResourceRole(userId, report.getCollectionId())
        .orElse(null);
    } else if (report.getData() instanceof ProcessReportDataDto &&
               ((ProcessReportDataDto) report.getData()).isInstantPreviewReport()) {
      role = isAuthorizedToAccessProcessReportDefinition(userId, (ProcessReportDataDto) report.getData()) ?
        RoleType.VIEWER : null;
    } else if (Optional.ofNullable(report.getOwner()).map(owner -> owner.equals(userId)).orElse(true)) {
      role = RoleType.EDITOR;
    }
    return Optional.ofNullable(role);
  }

  public boolean isAuthorizedToAccessReportDefinition(final String userId,
                                                      final ReportDefinitionDto<?> report) {
    final boolean authorizedToAccessDefinition;
    if (report instanceof SingleProcessReportDefinitionRequestDto) {
      final ProcessReportDataDto reportData = ((SingleProcessReportDefinitionRequestDto) report).getData();
      authorizedToAccessDefinition = isAuthorizedToAccessProcessReportDefinition(userId, reportData);
    } else if (report instanceof SingleDecisionReportDefinitionRequestDto) {
      final DecisionReportDataDto reportData = ((SingleDecisionReportDefinitionRequestDto) report).getData();
      authorizedToAccessDefinition = isAuthorizedToAccessDecisionReportDefinition(userId, reportData);
    } else if (report instanceof CombinedReportDefinitionRequestDto) {
      final CombinedReportDataDto reportData = ((CombinedReportDefinitionRequestDto) report).getData();
      authorizedToAccessDefinition = reportReader.getAllSingleProcessReportsForIdsOmitXml(reportData.getReportIds())
        .stream()
        .allMatch(r -> isAuthorizedToAccessProcessReportDefinition(userId, r.getData()));
    } else {
      throw new OptimizeRuntimeException("Unsupported report type: " + report.getClass().getSimpleName());
    }
    return authorizedToAccessDefinition;
  }

  private boolean isAuthorizedToAccessDecisionReportDefinition(@NonNull final String userId,
                                                               @NonNull final DecisionReportDataDto reportData) {
    return definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, DefinitionType.DECISION, reportData.getDecisionDefinitionKey(), reportData.getTenantIds()
    );
  }

  private boolean isAuthorizedToAccessProcessReportDefinition(@NonNull final String userId,
                                                              @NonNull final ProcessReportDataDto reportData) {
    return reportData.getDefinitions().stream()
      .allMatch(definition -> definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId, DefinitionType.PROCESS, definition.getKey(), definition.getTenantIds()
      ));
  }

}
