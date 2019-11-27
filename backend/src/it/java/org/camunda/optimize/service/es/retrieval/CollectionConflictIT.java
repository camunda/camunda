/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.assertj.core.api.Assertions;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class CollectionConflictIT extends AbstractIT {

  @Test
  public void getCollectionDeleteConflictsIfEntitiesAdded() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String firstDashboardId = createNewDashboardAndAddItToCollection(collectionId);
    String secondDashboardId = createNewDashboardAndAddItToCollection(collectionId);
    String reportId = createNewReportAndAddItToCollection(collectionId);
    String[] expectedConflictedItemIds = {firstDashboardId, secondDashboardId, reportId};

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ConflictResponseDto conflictResponseDto = getDeleteCollectionConflicts(collectionId);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COLLECTION, expectedConflictedItemIds);
  }

  @ParameterizedTest
  @MethodSource("scopeConflictScenarios")
  public void deleteSingleScopeFailsWithConflictIfUsedByWhenForceNotSet(ScopeConflictScenario conflictScenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(conflictScenario.getDefinitionType(), DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(
        collectionId,
        conflictScenario.getDefinitionType(),
        DEFAULT_DEFINITION_KEY,
        DEFAULT_TENANTS
      );
    final String alertId = alertClient.createAlertForReport(singleReportId);
    final String dashboardId = dashboardClient.createDashboard(collectionId, singletonList(singleReportId));

    // when
    ConflictResponseDto conflictResponse =
      deleteScopeFailsWithConflict(collectionId, scopeEntry.getId(), conflictScenario.getForceSetToFalse());

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.ALERT, new String[]{alertId});
    checkConflictedItems(conflictResponse, ConflictedItemType.DASHBOARD, new String[]{dashboardId});
  }

  @Test
  public void deleteSingleScopeConflictsContainCombinedReportIds() {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createCombinedReport(collectionId, singletonList(singleReportId));

    // when
    ConflictResponseDto conflictResponse =
      deleteScopeFailsWithConflict(collectionId, scopeEntry.getId(), false);

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.COMBINED_REPORT, new String[]{combinedReportId});
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getScopeDeletionConflicts_reportsAlertsAndDashboards(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(collectionId, definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String alertId = alertClient.createAlertForReport(singleReportId);
    final String dashboardId = dashboardClient.createDashboard(collectionId, singletonList(singleReportId));

    // when
    ConflictResponseDto conflictResponse =
      getScopeDeletionConflicts(collectionId, scopeEntry.getId());

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.ALERT, new String[]{alertId});
    checkConflictedItems(conflictResponse, ConflictedItemType.DASHBOARD, new String[]{dashboardId});
  }

  @Test
  public void getScopeDeletionConflicts_combinedReports() {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createCombinedReport(collectionId, singletonList(singleReportId));

    // when
    ConflictResponseDto conflictResponse =
      getScopeDeletionConflicts(collectionId, scopeEntry.getId());

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.COMBINED_REPORT, new String[]{combinedReportId});
  }

  @ParameterizedTest
  @MethodSource("scopeConflictScenarios")
  public void updateSingleScopeFailsWithConflictIfUsedByWhenForceNotSet(ScopeConflictScenario conflictScenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    addTenantToElasticsearch("tenantToBeRemovedFromScope");
    final ArrayList<String> tenants = new ArrayList<>(DEFAULT_TENANTS);
    tenants.add("tenantToBeRemovedFromScope");
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(conflictScenario.getDefinitionType(), DEFAULT_DEFINITION_KEY, tenants);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(
        collectionId,
        conflictScenario.getDefinitionType(),
        DEFAULT_DEFINITION_KEY,
        singletonList("tenantToBeRemovedFromScope")
      );
    final String alertId = alertClient.createAlertForReport(singleReportId);
    final String dashboardId = dashboardClient.createDashboard(collectionId, singletonList(singleReportId));

    // when
    tenants.remove("tenantToBeRemovedFromScope");
    ConflictResponseDto conflictResponse =
      updateScopeFailsWithConflict(collectionId, scopeEntry.getId(), tenants, conflictScenario.getForceSetToFalse());

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.ALERT, new String[]{alertId});
    checkConflictedItems(conflictResponse, ConflictedItemType.DASHBOARD, new String[]{dashboardId});
  }

  @Test
  public void updateSingleScopeConflictsContainCombinedReportIds() {
    // given
    String collectionId = collectionClient.createNewCollection();
    addTenantToElasticsearch("tenantToBeRemovedFromScope");
    final ArrayList<String> tenants = new ArrayList<>(DEFAULT_TENANTS);
    tenants.add("tenantToBeRemovedFromScope");
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, tenants);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(
        collectionId,
        PROCESS,
        DEFAULT_DEFINITION_KEY,
        singletonList("tenantToBeRemovedFromScope")
      );
    final String combinedReportId = reportClient.createCombinedReport(collectionId, singletonList(singleReportId));

    // when
    tenants.remove("tenantToBeRemovedFromScope");
    ConflictResponseDto conflictResponse =
      updateScopeFailsWithConflict(collectionId, scopeEntry.getId(), tenants, false);

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.COMBINED_REPORT, new String[]{combinedReportId});
  }

  private void addTenantToElasticsearch(final String tenantId) {
    TenantDto tenantDto = new TenantDto(tenantId, "ATenantName", DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantId, tenantDto);
  }

  private void checkConflictedItems(ConflictResponseDto conflictResponseDto,
                                    ConflictedItemType itemType,
                                    String[] expectedConflictedItemIds) {
    final Set<ConflictedItemDto> conflictedItemDtos = conflictResponseDto.getConflictedItems().stream()
      .filter(conflictedItemDto -> itemType.equals(conflictedItemDto.getType()))
      .collect(Collectors.toSet());

    Assertions.assertThat(conflictedItemDtos)
      .hasSize(expectedConflictedItemIds.length)
      .extracting(ConflictedItemDto::getId)
      .containsExactlyInAnyOrder(expectedConflictedItemIds);
  }

  private static Stream<ScopeConflictScenario> scopeConflictScenarios() {
    return Stream.of(
      new ScopeConflictScenario(PROCESS, false),
      new ScopeConflictScenario(PROCESS, null),
      new ScopeConflictScenario(DECISION, false),
      new ScopeConflictScenario(DECISION, null)
    );
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class ScopeConflictScenario {

    DefinitionType definitionType;
    Boolean forceSetToFalse;
  }

  private ConflictResponseDto deleteScopeFailsWithConflict(final String collectionId,
                                                           final String scopeId,
                                                           final Boolean force) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeId, force)
      .execute(ConflictResponseDto.class, 409);
  }

  private ConflictResponseDto updateScopeFailsWithConflict(final String collectionId,
                                                           final String scopeId,
                                                           final List<String> tenants,
                                                           final Boolean force) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        scopeId,
        new CollectionScopeEntryUpdateDto(tenants),
        force
      )
      .execute(ConflictResponseDto.class, 409);
  }

  private ConflictResponseDto getScopeDeletionConflicts(final String collectionId, final String scopeEntryId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetScopeDeletionConflictsRequest(collectionId, scopeEntryId)
      .execute(ConflictResponseDto.class, 200);
  }

  private String createNewDashboardAndAddItToCollection(String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewReportAndAddItToCollection(String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private ConflictResponseDto getDeleteCollectionConflicts(String id) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionDeleteConflictsRequest(id)
      .execute(ConflictResponseDto.class, 200);
  }
}
