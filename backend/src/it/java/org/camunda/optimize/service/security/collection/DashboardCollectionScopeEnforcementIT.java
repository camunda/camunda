/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.entity.EntityType.DASHBOARD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class DashboardCollectionScopeEnforcementIT extends AbstractIT {

  private static final List<ReportScenario> POSSIBLE_REPORT_SCENARIOS = ImmutableList
    .of(
      new ReportScenario(ReportType.PROCESS, false),
      new ReportScenario(ReportType.PROCESS, true),
      new ReportScenario(ReportType.DECISION, false)
    );

  private static List<ReportScenario> reportScenarios() {
    return POSSIBLE_REPORT_SCENARIOS;
  }

  @ParameterizedTest(name = "I can copy and move dashboard if the dashboard is in the scope with {0}")
  @MethodSource("reportScenarios")
  public void enforceScope_canCopyAndMoveDashboardIfInScope(final ReportScenario reportScenario) {
    // given
    final String authorizedTenant = "authorizedTenant";
    final List<String> tenants = singletonList(authorizedTenant);
    engineIntegrationExtension.createTenant(authorizedTenant);
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      "KEY_1",
      reportScenario.getReportType().toDefinitionType(),
      tenants
    );
    final String dashboardId = dashboardClient.createDashboard(null);
    final String reportId = createReportInCollection(reportScenario, null, "KEY_1", tenants);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));

    // when
    final Response response = dashboardClient.copyDashboardToCollectionAsUserAndGetRawResponse(
      dashboardId,
      collectionId,
      DEFAULT_USERNAME,
      DEFAULT_USERNAME
    );
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<EntityResponseDto> entities = entitiesClient.getAllEntities();

    // then
    assertThat(entities)
      .filteredOn(e -> e.getEntityType().equals(DASHBOARD))
      .hasSize(1)
      .extracting(EntityResponseDto::getId)
      .containsExactly(dashboardId);
  }

  @ParameterizedTest(name = "raise a conflict if one of the contained reports definition key is not in scope with {0}")
  @MethodSource("reportScenarios")
  public void enforceScope_raiseConflictIfOneOfTheContainedReportIsNotInScope(final ReportScenario reportScenario) {
    // given
    final String authorizedTenant = "authorizedTenant";
    final List<String> tenants = singletonList(authorizedTenant);
    engineIntegrationExtension.createTenant(authorizedTenant);
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      "KEY_1",
      reportScenario.getReportType().toDefinitionType(),
      tenants
    );
    final String dashboardId = dashboardClient.createDashboard(null);
    final String reportInScope = createReportInCollection(reportScenario, null, "KEY_1", tenants);
    final String reportNotInScope = createReportInCollection(reportScenario, null, "FOO", tenants);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportInScope));
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportNotInScope));

    // when
    final Response response = dashboardClient.copyDashboardToCollectionAsUserAndGetRawResponse(
      dashboardId,
      collectionId,
      DEFAULT_USERNAME,
      DEFAULT_USERNAME
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @ParameterizedTest(name = "raise a conflict if one of the contained reports tenants is not in scope with {0}")
  @MethodSource("reportScenarios")
  public void enforceScope_raiseConflictIfNotRightTenant(final ReportScenario reportScenario) {
    // given
    final String authorizedTenant = "authorizedTenant";
    final List<String> tenants = singletonList(authorizedTenant);
    engineIntegrationExtension.createTenant(authorizedTenant);
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      "KEY_1",
      reportScenario.getReportType().toDefinitionType(),
      tenants
    );
    final String dashboardId = dashboardClient.createDashboard(null);
    final String reportWithWrongTenant =
      createReportInCollection(reportScenario, null, "KEY_1", singletonList(null));
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportWithWrongTenant));

    // when
    final Response response = dashboardClient.copyDashboardToCollectionAsUserAndGetRawResponse(
      dashboardId,
      collectionId,
      DEFAULT_USERNAME,
      DEFAULT_USERNAME
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  private String createReportInCollection(final ReportScenario reportScenario,
                                         final String collectionId,
                                         final String definitionKey,
                                         final List<String> tenants) {
    switch (reportScenario.reportType) {
      case PROCESS:
        if (reportScenario.combined) {
          String singleReportId =
            reportClient.createSingleProcessReport(reportClient.createSingleProcessReportDefinitionDto(
            collectionId,
            definitionKey,
            tenants
          ));
          return reportClient.createCombinedReport(collectionId, Lists.newArrayList(singleReportId));
        } else {
          return
            reportClient.createSingleProcessReport(reportClient.createSingleProcessReportDefinitionDto(
              collectionId,
              definitionKey,
              tenants
            ));
        }
      case DECISION:
        return reportClient.createSingleDecisionReport(
          reportClient.createSingleDecisionReportDefinitionDto(
            collectionId,
            definitionKey,
            tenants
          ));
      default:
        throw new OptimizeIntegrationTestException("Unsupported reportType: " + reportScenario.reportType);
    }
  }

  @Data
  @AllArgsConstructor
  protected static class ReportScenario {

    ReportType reportType;
    boolean combined;
  }

}
