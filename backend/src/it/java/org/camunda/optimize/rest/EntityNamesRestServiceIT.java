/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;

public class EntityNamesRestServiceIT extends AbstractEntitiesRestServiceIT {

  @Test
  public void getEntityNames_WorksForAllPossibleEntities() {
    // given
    String reportId = addSingleReportToOptimize("aReportName", ReportType.PROCESS);
    String dashboardId = addDashboardToOptimize("aDashboardName");
    String collectionId = addCollection("aCollectionName");
    String eventProcessId = addEventProcessMappingToOptimize("anEventProcessName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameResponseDto result = entitiesClient.getEntityNames(collectionId, dashboardId, reportId, eventProcessId);

    // then
    assertThat(result.getCollectionName()).isEqualTo("aCollectionName");
    assertThat(result.getDashboardName()).isEqualTo("aDashboardName");
    assertThat(result.getReportName()).isEqualTo("aReportName");
    assertThat(result.getEventBasedProcessName()).isEqualTo("anEventProcessName");
  }

  @Test
  public void getEntityNames_ReturnsNoResponseForEventBasedProcessIfThereIsNone() {
    // given
    String reportId = addSingleReportToOptimize("aReportName", ReportType.PROCESS);
    String dashboardId = addDashboardToOptimize("aDashboardName");
    String collectionId = addCollection("aCollectionName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameResponseDto result = entitiesClient.getEntityNames(collectionId, dashboardId, reportId, "eventProcessId");

    // then
    assertThat(result.getCollectionName()).isEqualTo("aCollectionName");
    assertThat(result.getDashboardName()).isEqualTo("aDashboardName");
    assertThat(result.getReportName()).isEqualTo("aReportName");
    assertThat(result.getEventBasedProcessName()).isNull();
  }

  @Test
  public void getEntityNames_SeveralReportsDoNotDistortResult() {
    // given
    String reportId = addSingleReportToOptimize("aProcessReportName", ReportType.PROCESS);
    addSingleReportToOptimize("aDecisionReportName", ReportType.DECISION);
    addCombinedReport("aCombinedReportName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameResponseDto result = entitiesClient.getEntityNames(null, null, reportId, null);

    // then
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getDashboardName()).isNull();
    assertThat(result.getReportName()).isEqualTo("aProcessReportName");
    assertThat(result.getEventBasedProcessName()).isNull();
  }

  @Test
  public void getEntityNames_WorksForDecisionReports() {
    // given
    String reportId = addSingleReportToOptimize("aDecisionReportName", ReportType.DECISION);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameResponseDto result = entitiesClient.getEntityNames(null, null, reportId, null);

    // then
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getDashboardName()).isNull();
    assertThat(result.getReportName()).isEqualTo("aDecisionReportName");
    assertThat(result.getEventBasedProcessName()).isNull();
  }

  @Test
  public void getEntityNames_WorksForCombinedReports() {
    // given
    String reportId = addCombinedReport("aCombinedReportName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameResponseDto result = entitiesClient.getEntityNames(null, null, reportId, null);

    // then
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getDashboardName()).isNull();
    assertThat(result.getReportName()).isEqualTo("aCombinedReportName");
    assertThat(result.getEventBasedProcessName()).isNull();
  }

  @Test
  public void getEntityNames_NotAvailableIdReturns404() {
    // given
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(null, null, "notAvailableRequest", null))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void getEntityNames_NoIdProvidedReturns400() {
    // given
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(null, null, null, null))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEntityNames_usingMagicLinkReturnsOnlyDashboardName() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram("aDefinitionKey"));
    importAllEngineEntitiesFromScratch();

    // when
    final EntityNameResponseDto response = entitiesClient.getEntityNames("aDefinitionKey", "aDefinitionKey", null, null);

    // then
    assertThat(response.getCollectionName()).isNull();
    assertThat(response.getDashboardName()).isNotBlank();
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  private String addEventProcessMappingToOptimize(final String eventProcessName) {
    EventProcessMappingCreateRequestDto eventBasedProcessDto =
      EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
        .name(eventProcessName)
        .eventSources(Collections.singletonList(
          ExternalEventSourceEntryDto.builder()
            .configuration(ExternalEventSourceConfigDto.builder()
                             .includeAllGroups(true)
                             .eventScope(Collections.singletonList(EventScopeType.ALL))
                             .build())
            .build()))
        .build();
    return eventProcessClient.createEventProcessMapping(eventBasedProcessDto);
  }

}
