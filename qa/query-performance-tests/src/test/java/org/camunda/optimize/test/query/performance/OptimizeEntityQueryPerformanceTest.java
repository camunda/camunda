/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.query.performance;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.entityType;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.lastModified;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.lastModifier;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.name;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

@Slf4j
public class OptimizeEntityQueryPerformanceTest extends AbstractQueryPerformanceTest {

  public static final String PROCESS_DEFINITION_KEY = "processKey";
  public static final String DECISION_DEFINITION_KEY = "decisionKey";

  @BeforeEach
  public void init() {
    addTenantsToElasticsearch();
  }

  @Test
  public void testQueryPerformance_getProcessReports() {
    // given
    final int numberOfReports = getNumberOfEntities();
    addProcessDefinitionsToOptimize(numberOfReports);
    addSingleProcessReportsToOptimize(numberOfReports);

    // when & then
    final Supplier<List<ReportDefinitionDto>> reportSupplier = () -> embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
    assertThatListEndpointMaxAllowedQueryTimeIsMet(numberOfReports, reportSupplier);

    // when caches are warm due to previous call, then
    assertThatListEndpointMaxAllowedQueryTimeIsMetForWarmCaches(numberOfReports, reportSupplier);
  }

  @Test
  public void testQueryPerformance_getDecisionReports() {
    // given
    final int numberOfReports = getNumberOfEntities();
    addDecisionDefinitionsToOptimize(numberOfReports);
    addSingleDecisionReportsToOptimize(numberOfReports);

    // when & then
    final Supplier<List<ReportDefinitionDto>> reportSupplier = () -> embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
    assertThatListEndpointMaxAllowedQueryTimeIsMet(numberOfReports, reportSupplier);

    // when caches are warm due to previous call, then
    assertThatListEndpointMaxAllowedQueryTimeIsMetForWarmCaches(numberOfReports, reportSupplier);
  }

  @Test
  public void testQueryPerformance_getCombinedReports() {
    // given
    final int numberOfReports = getNumberOfEntities();
    addProcessDefinitionsToOptimize(numberOfReports);
    addCombinedReportsToOptimize(numberOfReports);

    // when & then
    final Supplier<List<ReportDefinitionDto>> reportSupplier = () -> embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
    assertThatListEndpointMaxAllowedQueryTimeIsMet(numberOfReports, reportSupplier);

    // when caches are warm due to previous call, then
    assertThatListEndpointMaxAllowedQueryTimeIsMetForWarmCaches(numberOfReports, reportSupplier);
  }

  @ParameterizedTest
  @MethodSource("entitySorters")
  public void testQueryPerformance_getAllEntities(EntitySorter entitySorter) {
    // given
    final int totalNumberOfEntities = getNumberOfEntities();
    // We consider 5 entity types: process reports / decision reports / combined reports / dashboards/ collections
    final int numOfEntityTypes = 5;
    final int numOfEachEntityToAdd = totalNumberOfEntities / numOfEntityTypes;
    // if the overall number is not divisible by the number of entity type, we add some extra dashboards
    final int extraDashboardsToAdd = totalNumberOfEntities % numOfEntityTypes;

    addProcessDefinitionsToOptimize(numOfEachEntityToAdd);
    addSingleProcessReportsToOptimize(numOfEachEntityToAdd);
    addDecisionDefinitionsToOptimize(numOfEachEntityToAdd);
    addSingleDecisionReportsToOptimize(numOfEachEntityToAdd);
    addCombinedReportsToOptimize(numOfEachEntityToAdd);
    addDashboardsToOptimize(numOfEachEntityToAdd + extraDashboardsToAdd);
    addCollectionsToOptimize(numOfEachEntityToAdd);

    // when & then
    final Supplier<List<EntityResponseDto>> reportSupplier = () -> embeddedOptimizeExtension.getRequestExecutor()
      .buildGetAllEntitiesRequest(entitySorter)
      .executeAndReturnList(EntityResponseDto.class, Response.Status.OK.getStatusCode());
    assertThatListEndpointMaxAllowedQueryTimeIsMet(totalNumberOfEntities, reportSupplier);

    // when caches are warm due to previous call, then
    assertThatListEndpointMaxAllowedQueryTimeIsMetForWarmCaches(totalNumberOfEntities, reportSupplier);
  }

  @ParameterizedTest
  @MethodSource("entitySorters")
  public void testQueryPerformance_getAllCollectionEntities(EntitySorter entitySorter) {
    // given
    final int totalNumberOfEntities = getNumberOfEntities();
    // We consider 4 entity types for a collection: process reports / decision reports / combined reports / dashboards
    final int numOfEntityTypesForCollection = 4;
    final int numOfEachEntityToAdd = totalNumberOfEntities / numOfEntityTypesForCollection;
    // if the overall number is not divisible by the number of entity type, we add some extra dashboards
    final int extraDashboardsToAdd = totalNumberOfEntities % numOfEntityTypesForCollection;

    final String collectionId = addCollectionToOptimize();
    addProcessDefinitionsToOptimize(numOfEachEntityToAdd);
    addSingleProcessReportsToOptimize(numOfEachEntityToAdd, collectionId);
    addDecisionDefinitionsToOptimize(numOfEachEntityToAdd);
    addSingleDecisionReportsToOptimize(numOfEachEntityToAdd, collectionId);
    addCombinedReportsToOptimize(numOfEachEntityToAdd, collectionId);
    addDashboardsToOptimize(numOfEachEntityToAdd + extraDashboardsToAdd, collectionId);

    // when & then
    final Supplier<List<EntityResponseDto>> reportSupplier = () -> embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionEntitiesRequest(collectionId, entitySorter)
      .executeAndReturnList(EntityResponseDto.class, Response.Status.OK.getStatusCode());
    assertThatListEndpointMaxAllowedQueryTimeIsMet(totalNumberOfEntities, reportSupplier);

    // when caches are warm due to previous call, then
    assertThatListEndpointMaxAllowedQueryTimeIsMetForWarmCaches(totalNumberOfEntities, reportSupplier);
  }

  @Test
  public void testQueryPerformance_getAlerts() {
    // given
    final int numberOfAlerts = getNumberOfEntities();

    final String collectionId = addCollectionToOptimize();
    addProcessDefinitionsToOptimize(numberOfAlerts);
    final List<String> reportIds = addSingleProcessReportsToOptimize(numberOfAlerts, collectionId);
    addAlertsToOptimize(numberOfAlerts, reportIds);

    // when & then
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      numberOfAlerts,
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .buildGetAlertsForCollectionRequest(collectionId)
        .executeAndReturnList(AlertDefinitionDto.class, Response.Status.OK.getStatusCode())
    );
  }

  private void addSingleProcessReportsToOptimize(final int numberOfReports) {
    addSingleProcessReportsToOptimize(numberOfReports, null);
  }

  private List<String> addSingleProcessReportsToOptimize(final int numberOfReports, final String collectionId) {
    final Map<String, Object> reportsById = new HashMap<>();
    IntStream.range(0, numberOfReports)
      .forEach(index -> {
        final ProcessReportDataDto processReportData = TemplatedProcessReportDataBuilder.createReportData()
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .setProcessDefinitionKey(PROCESS_DEFINITION_KEY + "_" + index)
          .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
          .build();

        final SingleProcessReportDefinitionRequestDto definition = new SingleProcessReportDefinitionRequestDto(
          processReportData);
        definition.setCollectionId(collectionId);
        definition.setName(IdGenerator.getNextId());
        definition.setOwner(DEFAULT_USER);
        definition.setLastModifier(DEFAULT_USER);
        definition.setLastModified(OffsetDateTime.now());
        definition.setId(IdGenerator.getNextId());
        reportsById.put(definition.getId(), definition);
      });
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(SINGLE_PROCESS_REPORT_INDEX_NAME, reportsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return new ArrayList<>(reportsById.keySet());
  }

  private void addSingleDecisionReportsToOptimize(final int numberOfReports) {
    addSingleDecisionReportsToOptimize(numberOfReports, null);
  }

  private void addSingleDecisionReportsToOptimize(final int numberOfReports, final String collectionId) {
    final Map<String, Object> reportsById = new HashMap<>();
    IntStream.range(0, numberOfReports)
      .forEach(index -> {
        final DecisionReportDataDto decisionReportData = DecisionReportDataBuilder.create()
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .setDecisionDefinitionKey(DECISION_DEFINITION_KEY + "_" + index)
          .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
          .build();
        final SingleDecisionReportDefinitionRequestDto definition = new SingleDecisionReportDefinitionRequestDto(
          decisionReportData);
        definition.setCollectionId(collectionId);
        definition.setName(IdGenerator.getNextId());
        definition.setOwner(DEFAULT_USER);
        definition.setLastModifier(DEFAULT_USER);
        definition.setLastModified(OffsetDateTime.now());
        definition.setId(IdGenerator.getNextId());
        reportsById.put(definition.getId(), definition);
      });
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(SINGLE_DECISION_REPORT_INDEX_NAME, reportsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void addCombinedReportsToOptimize(final int numberOfReports) {
    addCombinedReportsToOptimize(numberOfReports, null);
  }

  private void addCombinedReportsToOptimize(final int numberOfReports, final String collectionId) {
    final CombinedReportDataDto reportData = new CombinedReportDataDto();
    reportData.setReports(Arrays.asList(
      new CombinedReportItemDto("firstReportId", "red"),
      new CombinedReportItemDto("secondReportId", "blue")
    ));
    final CombinedReportDefinitionRequestDto definition =
      new CombinedReportDefinitionRequestDto(new CombinedReportDataDto());
    definition.setCollectionId(collectionId);
    definition.setName(IdGenerator.getNextId());
    definition.setOwner(DEFAULT_USER);
    definition.setLastModifier(DEFAULT_USER);
    definition.setLastModified(OffsetDateTime.now());

    Map<String, Object> reportsById = new HashMap<>();
    IntStream.range(0, numberOfReports)
      .forEach(index -> reportsById.put(IdGenerator.getNextId(), definition));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(COMBINED_REPORT_INDEX_NAME, reportsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void addDashboardsToOptimize(final int numOfDashboards) {
    addDashboardsToOptimize(numOfDashboards, null);
  }

  private void addDashboardsToOptimize(final int numOfDashboards, final String collectionId) {
    DashboardDefinitionRestDto definition = new DashboardDefinitionRestDto();
    definition.setTiles(Arrays.asList(
      DashboardReportTileDto.builder().id("firstReportId").type(DashboardTileType.OPTIMIZE_REPORT).build(),
      DashboardReportTileDto.builder().id("secondReportId").type(DashboardTileType.OPTIMIZE_REPORT).build()
    ));
    definition.setCollectionId(collectionId);
    definition.setName(IdGenerator.getNextId());
    definition.setOwner(DEFAULT_USER);
    definition.setLastModifier(DEFAULT_USER);
    definition.setLastModified(OffsetDateTime.now());

    Map<String, Object> dashboardsById = new HashMap<>();
    IntStream.range(0, numOfDashboards)
      .forEach(index -> dashboardsById.put(IdGenerator.getNextId(), definition));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(DASHBOARD_INDEX_NAME, dashboardsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private String addCollectionToOptimize() {
    CollectionDataDto collectionData = createCollectionData();
    CollectionDefinitionDto collection = createCollectionDefinition(collectionData);
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      COLLECTION_INDEX_NAME, ImmutableMap.of(collection.getId(), collection)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return collection.getId();
  }

  private void addCollectionsToOptimize(final int numOfCollections) {
    CollectionDataDto collectionData = createCollectionData();

    Map<String, Object> collectionsById = new HashMap<>();
    IntStream.range(0, numOfCollections)
      .forEach(index -> {
        CollectionDefinitionDto definition = createCollectionDefinition(collectionData);
        collectionsById.put(definition.getId(), definition);
      });
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(COLLECTION_INDEX_NAME, collectionsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // We add up to one of each entity randomly to each collection we have created
    for (String collectionId : collectionsById.keySet()) {
      if (RandomUtils.nextBoolean()) {
        addSingleProcessReportsToOptimize(1, collectionId);
      }
      if (RandomUtils.nextBoolean()) {
        addSingleDecisionReportsToOptimize(1, collectionId);
      }
      if (RandomUtils.nextBoolean()) {
        addCombinedReportsToOptimize(1, collectionId);
      }
      if (RandomUtils.nextBoolean()) {
        addDashboardsToOptimize(1, collectionId);
      }
    }
  }

  private void addAlertsToOptimize(final int numberOfAlerts, final List<String> reportIds) {
    final AlertDefinitionDto definition = new AlertDefinitionDto();
    definition.setReportId(reportIds.get(RandomUtils.nextInt(0, reportIds.size())));
    definition.setOwner(DEFAULT_USER);
    definition.setLastModifier(DEFAULT_USER);
    definition.setLastModified(OffsetDateTime.now());

    Map<String, Object> alertsById = new HashMap<>();
    IntStream.range(0, numberOfAlerts)
      .forEach(index -> alertsById.put(IdGenerator.getNextId(), definition));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(ALERT_INDEX_NAME, alertsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void addProcessDefinitionsToOptimize(final int numberOfDefinitions) {
    // creating as many definitions as we have entities
    final Map<String, Object> definitionsById = new HashMap<>();
    IntStream.rangeClosed(1, numberOfDefinitions)
      .mapToObj(String::valueOf)
      .forEach(definitionNumber -> IntStream.rangeClosed(1, getNumberOfDefinitionVersions())
        .mapToObj(String::valueOf)
        .forEach(version -> {
          final ProcessDefinitionOptimizeDto processDefinition = createProcessDefinition(definitionNumber, version);
          definitionsById.put(processDefinition.getId(), processDefinition);
        }));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, definitionsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void addDecisionDefinitionsToOptimize(final int numberOfDefinitions) {
    // creating as many definitions as we have entities
    final Map<String, Object> definitionsById = new HashMap<>();
    IntStream.rangeClosed(1, numberOfDefinitions)
      .mapToObj(String::valueOf)
      .forEach(definitionNumber -> IntStream.rangeClosed(1, getNumberOfDefinitionVersions())
        .mapToObj(String::valueOf)
        .forEach(version -> {
          final DecisionDefinitionOptimizeDto decisionDefinition = createDecisionDefinition(definitionNumber, version);
          definitionsById.put(decisionDefinition.getId(), decisionDefinition);
        }));
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(DECISION_DEFINITION_INDEX_NAME, definitionsById);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private CollectionDataDto createCollectionData() {
    CollectionDataDto collectionData = new CollectionDataDto();
    collectionData.setScope(Arrays.asList(
      new CollectionScopeEntryDto(DefinitionType.PROCESS, PROCESS_DEFINITION_KEY),
      new CollectionScopeEntryDto(DefinitionType.DECISION, DECISION_DEFINITION_KEY)
    ));
    final CollectionRoleRequestDto collectionRoleDto = new CollectionRoleRequestDto(
      new IdentityDto(DEFAULT_USER, IdentityType.USER),
      RoleType.MANAGER
    );
    collectionData.setRoles(Collections.singletonList(collectionRoleDto));
    return collectionData;
  }

  private CollectionDefinitionDto createCollectionDefinition(final CollectionDataDto collectionData) {
    CollectionDefinitionDto definition = new CollectionDefinitionDto();
    definition.setLastModifier(DEFAULT_USER);
    definition.setLastModified(OffsetDateTime.now());
    definition.setData(collectionData);
    definition.setId(IdGenerator.getNextId());
    definition.setName(IdGenerator.getNextId());
    return definition;
  }

  private static ProcessDefinitionOptimizeDto createProcessDefinition(final String definitionNumber,
                                                                      final String version) {
    return createProcessDefinition(
      PROCESS_DEFINITION_KEY + "_" + definitionNumber,
      String.valueOf(version),
      null,
      "processName",
      DEFAULT_ENGINE_ALIAS
    );
  }

  private static DecisionDefinitionOptimizeDto createDecisionDefinition(final String definitionNumber,
                                                                        final String version) {
    return createDecisionDefinition(
      DECISION_DEFINITION_KEY + "_" + definitionNumber,
      String.valueOf(version),
      null,
      "decisionName",
      DEFAULT_ENGINE_ALIAS
    );
  }

  private void addTenantsToElasticsearch() {
    final TenantDto tenantDto = new TenantDto("null", "null", DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntriesToElasticsearch(
      TENANT_INDEX_NAME,
      ImmutableMap.of(tenantDto.getId(), tenantDto)
    );
  }

  private static Stream<EntitySorter> entitySorters() {
    return Stream.of(
      null,
      new EntitySorter(name, SortOrder.ASC),
      new EntitySorter(name, SortOrder.DESC),
      new EntitySorter(entityType, SortOrder.ASC),
      new EntitySorter(entityType, SortOrder.DESC),
      new EntitySorter(lastModified, SortOrder.ASC),
      new EntitySorter(lastModified, SortOrder.DESC),
      new EntitySorter(lastModifier, SortOrder.ASC),
      new EntitySorter(lastModifier, SortOrder.DESC)
    );
  }

}
