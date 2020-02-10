/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.entity.EntityType.DASHBOARD;

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    final String collectionId = createNewCollection();
    createScopeWithTenants(collectionId, "KEY_1", tenants, reportScenario.getReportType().toDefinitionType());
    final String dashboardId = createPrivateDashboard();
    final String reportId = createReportInCollection(reportScenario, null, "KEY_1", tenants).getId();
    addSingleReportToDashboard(dashboardId, reportId);

    // when
    final Response response = copyDashboardToCollection(dashboardId, collectionId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<EntityDto> entities = getEntities();

    // then
    assertThat(entities)
      .filteredOn(e -> e.getEntityType().equals(DASHBOARD))
      .hasSize(1)
      .extracting(EntityDto::getId)
      .containsExactly(dashboardId);
  }

  @ParameterizedTest(name = "raise a conflict if one of the contained reports definition key is not in scope with {0}")
  @MethodSource("reportScenarios")
  public void enforceScope_raiseConflictIfOneOfTheContainedReportIsNotInScope(final ReportScenario reportScenario) {
    // given
    final String authorizedTenant = "authorizedTenant";
    final List<String> tenants = singletonList(authorizedTenant);
    engineIntegrationExtension.createTenant(authorizedTenant);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    final String collectionId = createNewCollection();
    createScopeWithTenants(collectionId, "KEY_1", tenants, reportScenario.getReportType().toDefinitionType());
    final String dashboardId = createPrivateDashboard();
    final String reportInScope = createReportInCollection(reportScenario, null, "KEY_1", tenants).getId();
    final String reportNotInScope = createReportInCollection(reportScenario, null, "FOO", tenants).getId();
    addSingleReportToDashboard(dashboardId, reportInScope);
    addSingleReportToDashboard(dashboardId, reportNotInScope);

    // when
    final Response response = copyDashboardToCollection(dashboardId, collectionId);

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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    final String collectionId = createNewCollection();
    createScopeWithTenants(collectionId, "KEY_1", tenants, reportScenario.getReportType().toDefinitionType());
    final String dashboardId = createPrivateDashboard();
    final String reportWithWrongTenant =
      createReportInCollection(reportScenario, null, "KEY_1", singletonList(null)).getId();
    addSingleReportToDashboard(dashboardId, reportWithWrongTenant);

    // when
    final Response response = copyDashboardToCollection(dashboardId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  private List<EntityDto> getEntities() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, Response.Status.OK.getStatusCode());
  }

  private void createScopeWithTenants(final String collectionId, final String definitionKey,
                                      final List<String> tenants, final DefinitionType definitionType) {
    final CollectionScopeEntryDto processScope = new CollectionScopeEntryDto(definitionType, definitionKey, tenants);
    addScopeEntryToCollection(collectionId, processScope);
  }

  private void addScopeEntryToCollection(final String collectionId, final CollectionScopeEntryDto entry) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, singletonList(entry))
      .execute(IdDto.class, Response.Status.NO_CONTENT.getStatusCode());
  }

  private Response addSingleReportToDashboard(final String dashboardId, final String privateReportId) {
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(privateReportId);

    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(
        dashboardId,
        new DashboardDefinitionDto(Collections.singletonList(reportLocationDto))
      )
      .execute();
  }

  private IdDto createReportInCollection(final ReportScenario reportScenario,
                                         final String collectionId,
                                         final String definitionKey,
                                         final List<String> tenants) {
    switch (reportScenario.reportType) {
      case PROCESS:
        if (reportScenario.combined) {
          final IdDto singleReportId = createReportInCollection(
            new ReportScenario(ReportType.PROCESS, false),
            collectionId,
            definitionKey,
            tenants
          );
          CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
          combinedReportDefinitionDto.setCollectionId(collectionId);
          final IdDto combinedReportId = embeddedOptimizeExtension
            .getRequestExecutor()
            .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
            .execute(IdDto.class, Response.Status.OK.getStatusCode());
          addSingleReportToCombinedReport(combinedReportId.getId(), singleReportId.getId());
          return combinedReportId;
        } else {
          SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
          singleProcessReportDefinitionDto.setCollectionId(collectionId);
          singleProcessReportDefinitionDto.getData().setProcessDefinitionKey(definitionKey);
          singleProcessReportDefinitionDto.getData().setTenantIds(tenants);
          return embeddedOptimizeExtension
            .getRequestExecutor()
            .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
            .execute(IdDto.class, Response.Status.OK.getStatusCode());
        }
      case DECISION:
        SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
        singleDecisionReportDefinitionDto.setCollectionId(collectionId);
        singleDecisionReportDefinitionDto.getData().setDecisionDefinitionKey(definitionKey);
        singleDecisionReportDefinitionDto.getData().setTenantIds(tenants);
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
          .execute(IdDto.class, Response.Status.OK.getStatusCode());
      default:
        throw new OptimizeIntegrationTestException("Unsupported reportType: " + reportScenario.reportType);
    }
  }

  private Response addSingleReportToCombinedReport(final String combinedReportId, final String reportId) {
    final CombinedReportDefinitionDto combinedReportData = new CombinedReportDefinitionDto();
    combinedReportData.getData().getReports().add(new CombinedReportItemDto(reportId, "red"));
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }

  private String createPrivateDashboard() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(null)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private Response copyDashboardToCollection(final String dashboardId, final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .execute();
  }

  private String createNewCollection() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  @Data
  @AllArgsConstructor
  protected static class ReportScenario {

    ReportType reportType;
    boolean combined;
  }

}
