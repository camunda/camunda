/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.service.util.ProcessReportDataType.RAW_DATA;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class PublicApiEntityIdRetrievalIT extends AbstractIT {
  private static final String ACCESS_TOKEN = "secret_export_token";

  @BeforeEach
  public void before() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

  @Test
  public void retrieveReportIdsFromCollection() {
    // given
    createAndSaveDefinitions();
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId1 = createProcessReport(collectionId);
    final String reportId2 = createDecisionReport(collectionId);
    dashboardClient.createDashboard(collectionId, Collections.singletonList(reportId1));

    final String otherCollection = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String otherCollReport = createProcessReport(otherCollection);
    dashboardClient.createDashboard(collectionId, Collections.singletonList(otherCollReport));

    // when
    final List<IdResponseDto> reportIds = publicApiClient.getAllReportIdsInCollection(collectionId, ACCESS_TOKEN);

    // then
    assertThat(reportIds).hasSize(2).extracting(IdResponseDto::getId).containsExactlyInAnyOrder(reportId1, reportId2);
  }

  @Test
  public void retrieveReportIdsFromNonExistentCollection() {
    // when
    final List<IdResponseDto> reportIds = publicApiClient.getAllReportIdsInCollection("fake_id", ACCESS_TOKEN);

    // then
    assertThat(reportIds).isEmpty();
  }

  @Test
  public void retrieveDashboardIdsFromCollection() {
    // given
    createAndSaveDefinitions();
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String reportId1 = createProcessReport(collectionId);
    final String reportId2 = createDecisionReport(collectionId);
    final String dashboardId1 = dashboardClient.createDashboard(collectionId, Collections.singletonList(reportId1));
    final String dashboardId2 = dashboardClient.createDashboard(collectionId, List.of(reportId1, reportId2));

    final String otherCollection = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String otherCollReport = createProcessReport(otherCollection);
    dashboardClient.createDashboard(otherCollection, Collections.singletonList(otherCollReport));

    // when
    final List<IdResponseDto> reportIds = publicApiClient.getAllDashboardIdsInCollection(collectionId, ACCESS_TOKEN);

    // then
    assertThat(reportIds).hasSize(2)
      .extracting(IdResponseDto::getId)
      .containsExactlyInAnyOrder(dashboardId1, dashboardId2);
  }

  @Test
  public void retrieveDashboardIdsFromNonExistentCollection() {
    // when
    final List<IdResponseDto> reportIds = publicApiClient.getAllDashboardIdsInCollection("fake_id", ACCESS_TOKEN);

    // then
    assertThat(reportIds).isEmpty();
  }

  private void createAndSaveDefinitions() {
    final ProcessDefinitionOptimizeDto processDefinition = ProcessDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(DEFAULT_DEFINITION_KEY)
      .name("processDefName")
      .version(ALL_VERSIONS)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml("processXmlString")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      processDefinition.getId(),
      processDefinition
    );
    final DecisionDefinitionOptimizeDto decisionDefinition = DecisionDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(DEFAULT_DEFINITION_KEY)
      .name("decisionDefName")
      .version(ALL_VERSIONS)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .dmn10Xml("DecisionDef")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME,
      decisionDefinition.getId(),
      decisionDefinition
    );
  }

  private String createProcessReport(final String collectionId) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFAULT_DEFINITION_KEY)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(RAW_DATA)
      .build();
    return reportClient.createSingleProcessReport(reportData, collectionId);
  }

  private String createDecisionReport(final String collectionId) {
    final DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setDefinitions(List.of(
      new ReportDataDefinitionDto(DEFAULT_DEFINITION_KEY, DEFAULT_DEFINITION_KEY, List.of(ALL_VERSIONS))
    ));
    return reportClient.createSingleDecisionReport(reportData, collectionId);
  }

}
