/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.List;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class BusinessKeyImportIT extends AbstractImportIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
  }

  @Test
  public void businessKeyImportedForRunningAndCompletedProcess() throws JsonProcessingException {
    // given
    ProcessInstanceEngineDto completedProcess = deployAndStartUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
    ProcessInstanceEngineDto runningProcess = deployAndStartUserTaskProcess();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getAllStoredBusinessKeys())
      .containsExactlyInAnyOrder(
        new BusinessKeyDto(completedProcess.getId(), completedProcess.getBusinessKey()),
        new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey())
      );

    // when running process is completed and import run again
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
    importAllEngineEntitiesFromLastIndex();

    // then keys are still correct
    assertThat(getAllStoredBusinessKeys())
      .containsExactlyInAnyOrder(
        new BusinessKeyDto(completedProcess.getId(), completedProcess.getBusinessKey()),
        new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey())
      );
  }

  @Test
  public void importOfBusinessKeyForRunningProcess_isImportedOnNextSuccessfulAttemptAfterEsFailures() throws JsonProcessingException {
    // given
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getAllStoredBusinessKeys()).isEmpty();

    // when updates to ES fail
    ProcessInstanceEngineDto runningProcess = deployAndStartUserTaskProcess();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest businessKeyImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + BUSINESS_KEY_INDEX_NAME + "\""));
    esMockServer
      .when(businessKeyImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importAllEngineEntitiesFromLastIndex();

    // then the key gets stored after successful write
    assertThat(getAllStoredBusinessKeys())
      .containsExactlyInAnyOrder(new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey()));
    esMockServer.verify(businessKeyImportMatcher);
  }

  @Test
  public void importOfBusinessKeyForCompletedProcess_isImportedOnNextSuccessfulAttemptAfterEsFailures() throws JsonProcessingException {
    // given
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getAllStoredBusinessKeys()).isEmpty();

    // when updates to ES fail
    ProcessInstanceEngineDto process = deployAndStartUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(process.getId());

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest businessKeyImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + BUSINESS_KEY_INDEX_NAME + "\""));
    esMockServer
      .when(businessKeyImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importAllEngineEntitiesFromLastIndex();

    // then the key gets stored after successful write
    assertThat(getAllStoredBusinessKeys())
      .containsExactlyInAnyOrder(new BusinessKeyDto(process.getId(), process.getBusinessKey()));
    esMockServer.verify(businessKeyImportMatcher);
  }

  @Test
  public void businessKeyNotImported_whenFeatureDisabled() throws JsonProcessingException {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);

    deployAndStartUserTaskProcess();
    ProcessInstanceEngineDto completedProcess = deployAndStartUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
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
