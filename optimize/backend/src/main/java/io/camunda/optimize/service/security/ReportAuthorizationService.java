/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ReportAuthorizationService {

  private final AbstractIdentityService identityService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final AuthorizedCollectionService collectionAuthorizationService;
  private final ReportReader reportReader;

  public ReportAuthorizationService(
      final AbstractIdentityService identityService,
      final DataSourceDefinitionAuthorizationService definitionAuthorizationService,
      final AuthorizedCollectionService collectionAuthorizationService,
      final ReportReader reportReader) {
    this.identityService = identityService;
    this.definitionAuthorizationService = definitionAuthorizationService;
    this.collectionAuthorizationService = collectionAuthorizationService;
    this.reportReader = reportReader;
  }

  public boolean isAuthorizedToReport(final String userId, final ReportDefinitionDto<?> report) {
    return getAuthorizedRole(userId, report).isPresent();
  }

  public Optional<RoleType> getAuthorizedRole(
      final String userId, final ReportDefinitionDto<?> report) {
    final Optional<RoleType> authorizedRole = getAuthorizedReportRole(userId, report);
    return authorizedRole.filter(role -> isAuthorizedToAccessReportDefinition(userId, report));
  }

  private Optional<RoleType> getAuthorizedReportRole(
      final String userId, final ReportDefinitionDto<?> report) {
    RoleType role = null;
    if (report.getCollectionId() != null) {
      role =
          collectionAuthorizationService
              .getUsersCollectionResourceRole(userId, report.getCollectionId())
              .orElse(null);
    } else if (report.getData() instanceof ProcessReportDataDto
        && ((ProcessReportDataDto) report.getData()).isInstantPreviewReport()) {
      role =
          isAuthorizedToAccessProcessReportDefinition(
                  userId, (ProcessReportDataDto) report.getData())
              ? RoleType.VIEWER
              : null;
    } else if (Optional.ofNullable(report.getOwner())
        .map(owner -> owner.equals(userId))
        .orElse(true)) {
      role = RoleType.EDITOR;
    }
    return Optional.ofNullable(role);
  }

  public boolean isAuthorizedToAccessReportDefinition(
      final String userId, final ReportDefinitionDto<?> report) {
    final boolean authorizedToAccessDefinition;
    if (report instanceof SingleProcessReportDefinitionRequestDto) {
      final ProcessReportDataDto reportData =
          ((SingleProcessReportDefinitionRequestDto) report).getData();
      authorizedToAccessDefinition =
          isAuthorizedToAccessProcessReportDefinition(userId, reportData);
    } else if (report instanceof SingleDecisionReportDefinitionRequestDto) {
      final DecisionReportDataDto reportData =
          ((SingleDecisionReportDefinitionRequestDto) report).getData();
      authorizedToAccessDefinition =
          isAuthorizedToAccessDecisionReportDefinition(userId, reportData);
    } else if (report instanceof CombinedReportDefinitionRequestDto) {
      final CombinedReportDataDto reportData =
          ((CombinedReportDefinitionRequestDto) report).getData();
      authorizedToAccessDefinition =
          reportReader.getAllSingleProcessReportsForIdsOmitXml(reportData.getReportIds()).stream()
              .allMatch(r -> isAuthorizedToAccessProcessReportDefinition(userId, r.getData()));
    } else {
      throw new OptimizeRuntimeException(
          "Unsupported report type: " + report.getClass().getSimpleName());
    }
    return authorizedToAccessDefinition;
  }

  private boolean isAuthorizedToAccessDecisionReportDefinition(
      final String userId, final DecisionReportDataDto reportData) {
    if (userId == null) {
      throw new IllegalArgumentException("userId cannot be null");
    }
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    return definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId,
        DefinitionType.DECISION,
        reportData.getDecisionDefinitionKey(),
        reportData.getTenantIds());
  }

  private boolean isAuthorizedToAccessProcessReportDefinition(
      final String userId, final ProcessReportDataDto reportData) {
    if (userId == null) {
      throw new IllegalArgumentException("userId cannot be null");
    }
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    return reportData.getDefinitions().stream()
        .allMatch(
            definition ->
                definitionAuthorizationService.isAuthorizedToAccessDefinition(
                    userId,
                    DefinitionType.PROCESS,
                    definition.getKey(),
                    definition.getTenantIds()));
  }
}
