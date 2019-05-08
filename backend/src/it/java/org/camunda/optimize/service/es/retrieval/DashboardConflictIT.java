/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntityUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@RunWith(JUnitParamsRunner.class)
public class DashboardConflictIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void getDashboardDeleteConflictsIfAddToCollection() {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String firstCollectionId = createNewCollectionAndAddDashboard(dashboardId);
    String secondCollectionId = createNewCollectionAndAddDashboard(dashboardId);
    String[] expectedConflictedItemIds = {firstCollectionId, secondCollectionId};

    // when
    ConflictResponseDto conflictResponseDto = getDashboardDeleteConflicts(dashboardId);

    // then
    checkConflictedItems(conflictResponseDto, expectedConflictedItemIds);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void deleteDashboardFailsWithConflictIfUsedByCollectionWhenForceSet(Boolean force) {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String firstCollectionId = createNewCollectionAndAddDashboard(dashboardId);
    String secondCollectionId = createNewCollectionAndAddDashboard(dashboardId);
    String[] expectedDashboardIds = {dashboardId};
    String[] expectedConflictedItemIds = {firstCollectionId, secondCollectionId};

    // when
    ConflictResponseDto conflictResponseDto = deleteDashboardFailWithConflict(dashboardId, force);

    // then
    checkConflictedItems(conflictResponseDto, expectedConflictedItemIds);
    checkDashboardsStillExist(expectedDashboardIds);
    checkCollectionsStillContainEntity(expectedConflictedItemIds, dashboardId);
  }

  private void checkCollectionsStillContainEntity(String[] expectedConflictedItemIds, String entityId) {
    List<ResolvedCollectionDefinitionDto> collections = getAllCollections();

    assertThat(collections.size(), is(expectedConflictedItemIds.length));
    assertThat(
      collections.stream().map(ResolvedCollectionDefinitionDto::getId).collect(Collectors.toSet()),
      containsInAnyOrder(expectedConflictedItemIds)
    );
    collections.forEach(collection -> {
      assertThat(collection.getData().getEntities().size(), is(1));
      assertThat(
        collection.getData().getEntities().stream().anyMatch(
          collectionEntity -> collectionEntity.getId().equals(entityId)
        ),
        is(true)
      );
    });
  }

  private void checkConflictedItems(ConflictResponseDto conflictResponseDto,
                                    String[] expectedConflictedItemIds) {
    final Set<ConflictedItemDto> conflictedItemDtos = conflictResponseDto.getConflictedItems().stream()
      .filter(conflictedItemDto -> ConflictedItemType.COLLECTION.equals(conflictedItemDto.getType()))
      .collect(Collectors.toSet());

    assertThat(conflictedItemDtos.size(), is(expectedConflictedItemIds.length));
    assertThat(
      conflictedItemDtos.stream().map(ConflictedItemDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(expectedConflictedItemIds)
    );
  }

  private void checkDashboardsStillExist(String[] expectedReportIds) {
    List<DashboardDefinitionDto> dashboards = getAllDashboards();
    assertThat(dashboards.size(), is(expectedReportIds.length));
    assertThat(
      dashboards.stream().map(DashboardDefinitionDto::getId).collect(Collectors.toSet()),
      containsInAnyOrder(expectedReportIds)
    );
  }

  private String createNewCollectionAndAddDashboard(String dashboardId) {
    String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();

    final CollectionEntityUpdateDto collectionEntityUpdateDto = new CollectionEntityUpdateDto();
    collectionEntityUpdateDto.setEntityId(dashboardId);

    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildAddEntityToCollectionRequest(id, collectionEntityUpdateDto)
      .execute();
    assertThat(response.getStatus(), is(204));

    return id;
  }

  private List<DashboardDefinitionDto> getAllDashboards() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllDashboardsRequest()
      .executeAndReturnList(DashboardDefinitionDto.class, 200);
  }

  private List<ResolvedCollectionDefinitionDto> getAllCollections() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllCollectionsRequest()
      .executeAndReturnList(ResolvedCollectionDefinitionDto.class, 200);
  }

  private ConflictResponseDto getDashboardDeleteConflicts(String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardDeleteConflictsRequest(id)
      .execute(ConflictResponseDto.class, 200);
  }

  private ConflictResponseDto deleteDashboardFailWithConflict(String dashboardId, Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, force)
      .execute(ConflictResponseDto.class, 409);

  }

  private String addEmptyDashboardToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  public static class ForceParameterProvider {
    public static Object[] provideForceParameterAsBoolean() {
      return new Object[]{
        new Object[]{null},
        new Object[]{false},
      };
    }
  }
}
