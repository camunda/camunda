/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;

public class BusinessKeyImportIT extends AbstractImportIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
  }

  @Test
  public void businessKeyImportedForRunningAndCompletedProcess() throws JsonProcessingException {
    // given
    ProcessInstanceEngineDto completedProcess = deployAndStartUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
    ProcessInstanceEngineDto runningProcess = deployAndStartUserTaskProcess();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getAllStoredBusinessKeys())
      .containsExactlyInAnyOrder(
        new BusinessKeyDto(completedProcess.getId(), completedProcess.getBusinessKey()),
        new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey())
      );

    // when running process is completed and import run again
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then keys are still correct
    assertThat(getAllStoredBusinessKeys())
      .containsExactlyInAnyOrder(
        new BusinessKeyDto(completedProcess.getId(), completedProcess.getBusinessKey()),
        new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey())
      );
  }

  @Test
  public void businessKeyNotImportedWhenFeatureDisabled() throws JsonProcessingException {
    //given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);

    deployAndStartUserTaskProcess();
    ProcessInstanceEngineDto completedProcess = deployAndStartUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    List<BusinessKeyDto> storedBusinessKeys = getAllStoredBusinessKeys();
    assertThat(storedBusinessKeys).isEmpty();
  }

  private List<BusinessKeyDto> getAllStoredBusinessKeys() throws JsonProcessingException {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      BUSINESS_KEY_INDEX_NAME);
    List<BusinessKeyDto> businessKeyDtos = new ArrayList<>();
    for (SearchHit searchHitFields : response.getHits()) {
      final BusinessKeyDto businessKeyDto = embeddedOptimizeExtension.getObjectMapper().readValue(
        searchHitFields.getSourceAsString(), BusinessKeyDto.class);
      businessKeyDtos.add(businessKeyDto);
    }
    return businessKeyDtos;
  }

}
