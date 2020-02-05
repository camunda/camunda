/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.CamundaActivityEventIndex;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.service.CamundaEventService.END_MAPPED_SUFFIX;
import static org.camunda.optimize.service.CamundaEventService.PROCESS_END_TYPE;
import static org.camunda.optimize.service.CamundaEventService.PROCESS_START_TYPE;
import static org.camunda.optimize.service.CamundaEventService.START_MAPPED_SUFFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;

public class CamundaActivityEventImportIT extends AbstractImportIT {

  private static final String START_EVENT = ActivityTypes.START_EVENT;
  private static final String END_EVENT = ActivityTypes.END_EVENT_NONE;
  private static final String USER_TASK = ActivityTypes.TASK_USER_TASK;

  private OptimizeIndexNameService indexNameService;

  @BeforeEach
  public void init() {
    indexNameService = embeddedOptimizeExtension.getOptimizeElasticClient().getIndexNameService();
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
  }

  @Test
  public void expectedEventsAreCreatedOnImportOfCompletedProcess() throws IOException {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("eventsDef");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());

    assertThat(storedEvents)
      .hasSize(6)
      .usingElementComparatorIgnoringFields(
        CamundaActivityEventDto.Fields.activityInstanceId,
        CamundaActivityEventDto.Fields.processDefinitionName,
        CamundaActivityEventDto.Fields.engine,
        CamundaActivityEventDto.Fields.timestamp
      )
      .containsExactlyInAnyOrder(
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, processInstanceEngineDto),
        createAssertionEvent(END_EVENT, END_EVENT, END_EVENT, processInstanceEngineDto),
        createAssertionEvent(
          addDelimiterForStrings(USER_TASK, START_MAPPED_SUFFIX),
          addDelimiterForStrings(USER_TASK, START_MAPPED_SUFFIX),
          USER_TASK,
          processInstanceEngineDto
        ),
        createAssertionEvent(
          addDelimiterForStrings(USER_TASK, END_MAPPED_SUFFIX),
          addDelimiterForStrings(USER_TASK, END_MAPPED_SUFFIX),
          USER_TASK,
          processInstanceEngineDto
        ),
        createAssertionEvent(
          addDelimiterForStrings(processInstanceEngineDto.getProcessDefinitionKey(), PROCESS_START_TYPE),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          processInstanceEngineDto
        ),
        createAssertionEvent(
          addDelimiterForStrings(processInstanceEngineDto.getProcessDefinitionKey(), PROCESS_END_TYPE),
          PROCESS_END_TYPE,
          PROCESS_END_TYPE,
          processInstanceEngineDto
        )
      );
  }

  @Test
  public void expectedEventsCreatedOnImportOfRunningProcess() throws IOException {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("runningActivities");

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());

    assertThat(storedEvents)
      .hasSize(3)
      .usingElementComparatorIgnoringFields(
        CamundaActivityEventDto.Fields.activityInstanceId,
        CamundaActivityEventDto.Fields.processDefinitionName,
        CamundaActivityEventDto.Fields.engine,
        CamundaActivityEventDto.Fields.timestamp
      )
      .containsExactlyInAnyOrder(
        createAssertionEvent(
          addDelimiterForStrings(processInstanceEngineDto.getProcessDefinitionKey(), PROCESS_START_TYPE),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          processInstanceEngineDto
        ),
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, processInstanceEngineDto),
        createAssertionEvent(
          addDelimiterForStrings(USER_TASK, START_MAPPED_SUFFIX),
          addDelimiterForStrings(USER_TASK, START_MAPPED_SUFFIX),
          USER_TASK,
          processInstanceEngineDto
        )
      );
  }

  @Test
  public void noEventsCreatedOnImportWithFeatureDisabled() throws JsonProcessingException {
    // given the index has been created, the process start, the start Event, and start of user task has been saved already
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("noEventsDef");
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    List<CamundaActivityEventDto> initialStoredEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    assertThat(initialStoredEvents)
      .hasSize(3)
      .extracting(CamundaActivityEventDto::getActivityId)
      .containsExactlyInAnyOrder(
        START_EVENT,
        addDelimiterForStrings(USER_TASK, START_MAPPED_SUFFIX),
        addDelimiterForStrings(
          processInstanceEngineDto.getProcessDefinitionKey(),
          PROCESS_START_TYPE
        )
      );

    // when the feature is disabled
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);

    // when engine events happen and import triggered
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then no additional events are stored
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    assertThat(storedEvents).isEqualTo(storedEvents);
  }

  @Test
  public void expectedIndicesCreatedWithMultipleDefinitionsImportedInSameBatch() throws IOException {
    // given
    ProcessInstanceEngineDto firstProcessInstanceEngineDto = deployAndStartUserTaskProcessWithName("firstProcessSameBatch");
    ProcessInstanceEngineDto secondProcessInstanceEngineDto = deployAndStartUserTaskProcessWithName("secondProcessSameBatch");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(
      createExpectedIndexNameForProcessDefinition(firstProcessInstanceEngineDto.getProcessDefinitionKey()),
      createExpectedIndexNameForProcessDefinition(secondProcessInstanceEngineDto.getProcessDefinitionKey())
    );
    assertThat(esClient.exists(request, RequestOptions.DEFAULT)).isTrue();

    // then events have been saved in each index
    assertThat(getSavedEventsForProcessDefinitionKey(firstProcessInstanceEngineDto.getProcessDefinitionKey())).hasSize(6);
    assertThat(getSavedEventsForProcessDefinitionKey(secondProcessInstanceEngineDto.getProcessDefinitionKey())).hasSize(6);
  }

  @Test
  public void expectedIndicesCreatedWithMultipleDefinitionsImportedInMultipleBatches() throws IOException {
    // given
    ProcessInstanceEngineDto firstProcessInstanceEngineDto = deployAndStartUserTaskProcessWithName("aProcessFirstBatch");
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    ProcessInstanceEngineDto secondProcessInstanceEngineDto = deployAndStartUserTaskProcessWithName("aProcessSecondBatch");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(
      createExpectedIndexNameForProcessDefinition(firstProcessInstanceEngineDto.getProcessDefinitionKey()),
      createExpectedIndexNameForProcessDefinition(secondProcessInstanceEngineDto.getProcessDefinitionKey())
    );
    assertThat(esClient.exists(request, RequestOptions.DEFAULT)).isTrue();

    // then events have been saved in each index. The conversion to set is to remove duplicate entries due to multiple import batches
    Set<String> idsInFirstIndex = getSavedEventsForProcessDefinitionKey(firstProcessInstanceEngineDto.getProcessDefinitionKey())
      .stream()
      .map(CamundaActivityEventDto::getActivityId)
      .collect(Collectors.toSet());
    assertThat(idsInFirstIndex).hasSize(6);
    assertThat(getSavedEventsForProcessDefinitionKey(secondProcessInstanceEngineDto.getProcessDefinitionKey())).hasSize(6);
  }

  @Test
  public void noIndexCreatedOnImportWithFeatureDisabled() throws IOException {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("shouldNotBeCreated");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(
      createExpectedIndexNameForProcessDefinition(processInstanceEngineDto.getProcessDefinitionKey()));
    assertThat(esClient.exists(request, RequestOptions.DEFAULT)).isFalse();
  }

  private CamundaActivityEventDto createAssertionEvent(String activityId, String activityName, String activityType,
                                                       ProcessInstanceEngineDto processInstanceEngineDto) {
    return CamundaActivityEventDto.builder()
      .activityId(activityId)
      .activityName(activityName)
      .activityType(activityType)
      .processInstanceId(processInstanceEngineDto.getId())
      .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .processDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .tenantId(processInstanceEngineDto.getTenantId())
      .build();
  }

  private String createExpectedIndexNameForProcessDefinition(final String processDefinitionKey) {
    return indexNameService.getVersionedOptimizeIndexNameForIndexMapping(new CamundaActivityEventIndex(processDefinitionKey));
  }

  private List<CamundaActivityEventDto> getSavedEventsForProcessDefinitionKey(final String processDefinitionKey) throws
                                                                                                                 JsonProcessingException {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + processDefinitionKey);
    List<CamundaActivityEventDto> storedEvents = new ArrayList<>();
    for (SearchHit searchHitFields : response.getHits()) {
      final CamundaActivityEventDto camundaActivityEventDto = embeddedOptimizeExtension.getObjectMapper().readValue(
        searchHitFields.getSourceAsString(), CamundaActivityEventDto.class);
      storedEvents.add(camundaActivityEventDto);
    }
    return storedEvents;
  }

  private static String addDelimiterForStrings(String... strings) {
    return String.join("_", strings);
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithName(String processName) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(processName)
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

}
