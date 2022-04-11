/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class CollectionConflictIT extends AbstractIT {

  @Test
  public void getCollectionDeleteConflictsIfEntitiesAdded() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String firstDashboardId = dashboardClient.createEmptyDashboard(collectionId);
    String secondDashboardId = dashboardClient.createEmptyDashboard(collectionId);
    String reportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    String[] expectedConflictedItemIds = {firstDashboardId, secondDashboardId, reportId};

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ConflictResponseDto conflictResponseDto = collectionClient.getDeleteCollectionConflicts(collectionId);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COLLECTION, expectedConflictedItemIds);
  }

  @Test
  public void checkDeleteConflictsForBulkDeleteOfCollectionScope_withoutAuthentication_fails() {
    // when
    Response response = checkScopeBulkDeletionConflictsNoAuth();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void checkDeleteConflictsForBulkDeleteOfCollectionScope_forUnauthorizedUser_fails() {
    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    String collectionId = collectionClient.createNewCollection();
    Response response = checkScopeBulkDeletionConflictsWithUserAuthentication(collectionId);

    // then the status code is forbidden
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
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
    assertThat(conflictResponse.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.ALERT, new String[]{alertId});
    checkConflictedItems(conflictResponse, ConflictedItemType.DASHBOARD, new String[]{dashboardId});
  }

  @Test
  public void deleteSingleScopeFailsWithConflictForMultiDefinitionProcessReportIfUsedByWhenForceNotSet() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    final String definitionKey1 = DEFAULT_DEFINITION_KEY;
    final CollectionScopeEntryDto scopeEntry1 = new CollectionScopeEntryDto(PROCESS, definitionKey1, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    final String definitionKey2 = "key2";
    final CollectionScopeEntryDto scopeEntry2 = new CollectionScopeEntryDto(PROCESS, definitionKey2, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(new ReportDataDefinitionDto(definitionKey1), new ReportDataDefinitionDto(definitionKey2)))
      .build();
    final String singleReportId = reportClient.createSingleProcessReport(reportData, collectionId);
    final String alertId = alertClient.createAlertForReport(singleReportId);
    final String dashboardId = dashboardClient.createDashboard(collectionId, singletonList(singleReportId));

    // when
    final ConflictResponseDto conflictResponse = deleteScopeFailsWithConflict(collectionId, scopeEntry2.getId(), false);

    // then
    assertThat(conflictResponse.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
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
    assertThat(conflictResponse.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
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
      collectionClient.getScopeDeletionConflicts(collectionId, scopeEntry.getId());

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.ALERT, new String[]{alertId});
    checkConflictedItems(conflictResponse, ConflictedItemType.DASHBOARD, new String[]{dashboardId});
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void checkScopeBulkDeletionConflicts_dashboardConflictInCollection(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    final String singleReportId = reportClient.createSingleReport(
      collectionId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    );
    dashboardClient.createDashboard(collectionId, singletonList(singleReportId));
    final CollectionScopeEntryDto scopeEntry2 = new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    List<String> collectionScopeIds = Arrays.asList(scopeEntry1.getId(), scopeEntry2.getId());

    // when
    boolean response = collectionClient.collectionScopesHaveDeleteConflict(collectionId, collectionScopeIds);

    // then
    assertThat(response).isTrue();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void checkScopeBulkDeletionConflicts_alertConflictInCollection(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    final String singleReportId = reportClient.createSingleReport(
      collectionId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    );
    alertClient.createAlertForReport(singleReportId);
    final CollectionScopeEntryDto scopeEntry2 = new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    List<String> collectionScopeIds = Arrays.asList(scopeEntry1.getId(), scopeEntry2.getId());

    // when
    boolean response = collectionClient.collectionScopesHaveDeleteConflict(collectionId, collectionScopeIds);

    // then
    assertThat(response).isTrue();
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
      collectionClient.getScopeDeletionConflicts(collectionId, scopeEntry.getId());

    // then
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
    checkConflictedItems(conflictResponse, ConflictedItemType.COMBINED_REPORT, new String[]{combinedReportId});
  }

  @Test
  public void scopeBulkDeletionHasConflicts_combinedReports() {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    final CollectionScopeEntryDto scopeEntry2 =
      new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    final String singleReportId =
      reportClient.createSingleReport(collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    reportClient.createCombinedReport(collectionId, singletonList(singleReportId));
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);
    final CollectionScopeEntryDto scopeEntry3 =
      new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    final CollectionScopeEntryDto scopeEntry4 =
      new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);

    List<String> collectionScopeIds1 = Arrays.asList(scopeEntry1.getId(), scopeEntry2.getId());
    List<String> collectionScopeId2 = Arrays.asList(scopeEntry3.getId(), scopeEntry4.getId());

    // when
    boolean response = collectionClient.collectionScopesHaveDeleteConflict(collectionId, collectionScopeIds1);

    // then
    assertThat(response).isTrue();

    // when
    response = collectionClient.collectionScopesHaveDeleteConflict(collectionId, collectionScopeId2);

    // then
    assertThat(response).isFalse();
  }

  @Test
  public void scopeBulkDeletionNoConflicts() {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final CollectionScopeEntryDto scopeEntry2 =
      new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    List<String> collectionScopeIds = Arrays.asList(scopeEntry1.getId(), scopeEntry2.getId());

    // when
    boolean response = collectionClient.collectionScopesHaveDeleteConflict(collectionId, collectionScopeIds);

    // then
    assertThat(response).isFalse();
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

    // when
    tenants.remove("tenantToBeRemovedFromScope");
    ConflictResponseDto conflictResponse =
      updateScopeFailsWithConflict(collectionId, scopeEntry.getId(), tenants, conflictScenario.getForceSetToFalse());

    // then
    assertThat(conflictResponse.getConflictedItems()).hasSize(1);
    checkConflictedItems(conflictResponse, ConflictedItemType.REPORT, new String[]{singleReportId});
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

    assertThat(conflictedItemDtos)
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
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());
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
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());
  }

  private Response checkScopeBulkDeletionConflictsNoAuth() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckScopeBulkDeletionConflictsRequest("doesntMatter", Collections.emptyList())
      .withoutAuthentication().execute();
  }

  private Response checkScopeBulkDeletionConflictsWithUserAuthentication(String collectionId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckScopeBulkDeletionConflictsRequest(collectionId, Collections.emptyList())
      .withUserAuthentication(KERMIT_USER, KERMIT_USER).execute();
  }

}
