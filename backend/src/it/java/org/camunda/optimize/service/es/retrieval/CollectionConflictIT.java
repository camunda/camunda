/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionConflictIT extends AbstractIT {

  @Test
  public void getCollectionDeleteConflictsIfEntitiesAdded() {
    // given
    String collectionId = addEmptyCollectionToOptimize();
    String firstDashboardId = createNewDashboardAddedToCollection(collectionId);
    String secondDashboardId = createNewDashboardAddedToCollection(collectionId);
    String reportId = createNewReportAddedToCollection(collectionId);
    String[] expectedConflictedItemIds = {firstDashboardId, secondDashboardId, reportId};

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ConflictResponseDto conflictResponseDto = getDeleteCollectionConflicts(collectionId);

    // then
    checkConflictedItems(conflictResponseDto, expectedConflictedItemIds);
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

  private String createNewDashboardAddedToCollection(String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewReportAddedToCollection(String collectionId) {
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

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}
