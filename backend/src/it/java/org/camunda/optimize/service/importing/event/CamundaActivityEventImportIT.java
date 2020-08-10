/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.importing.AbstractImportIT;
import org.camunda.optimize.util.BpmnModels;
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
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_END_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_START_TYPE;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class CamundaActivityEventImportIT extends AbstractImportIT {

  private static final String START_EVENT = ActivityTypes.START_EVENT;
  private static final String END_EVENT = ActivityTypes.END_EVENT_NONE;
  private static final String USER_TASK = ActivityTypes.TASK_USER_TASK;
  private static final String USER_TASK_2 = USER_TASK + "_2";

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
  }

  @Test
  public void expectedEventsAreCreatedOnImportOfCompletedProcess() throws IOException {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("eventsDef");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());

    assertThat(storedEvents)
      .hasSize(6)
      .usingElementComparatorIgnoringFields(
        CamundaActivityEventDto.Fields.activityInstanceId,
        CamundaActivityEventDto.Fields.processDefinitionVersion,
        CamundaActivityEventDto.Fields.engine,
        CamundaActivityEventDto.Fields.timestamp,
        CamundaActivityEventDto.Fields.orderCounter
      )
      .containsExactlyInAnyOrder(
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, processInstanceEngineDto),
        createAssertionEvent(END_EVENT, END_EVENT, END_EVENT, processInstanceEngineDto),
        createAssertionEvent(
          applyCamundaTaskStartEventSuffix(USER_TASK),
          applyCamundaTaskStartEventSuffix(USER_TASK),
          USER_TASK,
          processInstanceEngineDto
        ),
        createAssertionEvent(
          applyCamundaTaskEndEventSuffix(USER_TASK),
          applyCamundaTaskEndEventSuffix(USER_TASK),
          USER_TASK,
          processInstanceEngineDto
        ),
        createAssertionEvent(
          applyCamundaProcessInstanceStartEventSuffix(processInstanceEngineDto.getProcessDefinitionKey()),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          processInstanceEngineDto
        ),
        createAssertionEvent(
          applyCamundaProcessInstanceEndEventSuffix(processInstanceEngineDto.getProcessDefinitionKey()),
          PROCESS_END_TYPE,
          PROCESS_END_TYPE,
          processInstanceEngineDto
        )
      )
      .extracting(CamundaActivityEventDto.Fields.activityInstanceId)
      .contains(
        applyCamundaProcessInstanceEndEventSuffix(processInstanceEngineDto.getId()),
        applyCamundaProcessInstanceStartEventSuffix(processInstanceEngineDto.getId())
      );
    assertOrderCounters(storedEvents);
  }

  @Test
  public void expectedEventsCreatedOnImportOfRunningProcess() throws IOException {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("runningActivities");

    // when
    importAllEngineEntitiesFromScratch();

    // then
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());

    assertThat(storedEvents)
      .hasSize(3)
      .usingElementComparatorIgnoringFields(
        CamundaActivityEventDto.Fields.activityInstanceId,
        CamundaActivityEventDto.Fields.processDefinitionVersion,
        CamundaActivityEventDto.Fields.engine,
        CamundaActivityEventDto.Fields.timestamp,
        CamundaActivityEventDto.Fields.orderCounter
      )
      .containsExactlyInAnyOrder(
        createAssertionEvent(
          applyCamundaProcessInstanceStartEventSuffix(processInstanceEngineDto.getProcessDefinitionKey()),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          processInstanceEngineDto
        ),
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, processInstanceEngineDto),
        createAssertionEvent(
          applyCamundaTaskStartEventSuffix(USER_TASK),
          applyCamundaTaskStartEventSuffix(USER_TASK),
          USER_TASK,
          processInstanceEngineDto
        )
      )
      .extracting(CamundaActivityEventDto.Fields.activityInstanceId)
      .contains(applyCamundaProcessInstanceStartEventSuffix(processInstanceEngineDto.getId()));
    assertOrderCounters(storedEvents);
  }

  @Test
  public void expectedEventsCreatedOnImportOfMultipleInstances() throws IOException {
    // given
    final ProcessInstanceEngineDto firstInstance = deployAndStartUserTaskProcessWithName("processName");
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId());
    secondInstance.setProcessDefinitionKey(firstInstance.getProcessDefinitionKey());
    secondInstance.setProcessDefinitionVersion(firstInstance.getProcessDefinitionVersion());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(firstInstance.getProcessDefinitionKey());

    assertThat(storedEvents)
      .hasSize(6)
      .usingElementComparatorIgnoringFields(
        CamundaActivityEventDto.Fields.activityInstanceId,
        CamundaActivityEventDto.Fields.processDefinitionVersion,
        CamundaActivityEventDto.Fields.engine,
        CamundaActivityEventDto.Fields.timestamp,
        CamundaActivityEventDto.Fields.orderCounter
      )
      .containsExactlyInAnyOrder(
        createAssertionEvent(
          applyCamundaProcessInstanceStartEventSuffix(firstInstance.getProcessDefinitionKey()),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          firstInstance
        ),
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, firstInstance),
        createAssertionEvent(
          applyCamundaTaskStartEventSuffix(USER_TASK),
          applyCamundaTaskStartEventSuffix(USER_TASK),
          USER_TASK,
          firstInstance
        ),
        createAssertionEvent(
          applyCamundaProcessInstanceStartEventSuffix(secondInstance.getProcessDefinitionKey()),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          secondInstance
        ),
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, secondInstance),
        createAssertionEvent(
          applyCamundaTaskStartEventSuffix(USER_TASK),
          applyCamundaTaskStartEventSuffix(USER_TASK),
          USER_TASK,
          secondInstance
        )
      )
      .extracting(CamundaActivityEventDto.Fields.activityInstanceId)
      .contains(
        applyCamundaProcessInstanceStartEventSuffix(firstInstance.getId()),
        applyCamundaProcessInstanceStartEventSuffix(secondInstance.getId())
      );
    assertOrderCounters(storedEvents);
  }

  @Test
  public void expectedEventsCreatedOnImportOfMultipleSplitEventsFromSameModel() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstance = deployAndStartTwoUserTasksProcess("processName");
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstance.getProcessDefinitionKey());

    assertThat(storedEvents)
      .hasSize(8)
      .usingElementComparatorIgnoringFields(
        CamundaActivityEventDto.Fields.activityInstanceId,
        CamundaActivityEventDto.Fields.processDefinitionVersion,
        CamundaActivityEventDto.Fields.engine,
        CamundaActivityEventDto.Fields.timestamp,
        CamundaActivityEventDto.Fields.orderCounter
      )
      .containsExactlyInAnyOrder(
        createAssertionEvent(
          applyCamundaProcessInstanceStartEventSuffix(processInstance.getProcessDefinitionKey()),
          PROCESS_START_TYPE,
          PROCESS_START_TYPE,
          processInstance
        ),
        createAssertionEvent(START_EVENT, START_EVENT, START_EVENT, processInstance),
        createAssertionEvent(
          applyCamundaTaskStartEventSuffix(USER_TASK),
          applyCamundaTaskStartEventSuffix(USER_TASK),
          USER_TASK,
          processInstance
        ),
        createAssertionEvent(
          applyCamundaTaskEndEventSuffix(USER_TASK),
          applyCamundaTaskEndEventSuffix(USER_TASK),
          USER_TASK,
          processInstance
        ),
        createAssertionEvent(
          applyCamundaTaskStartEventSuffix(USER_TASK_2),
          applyCamundaTaskStartEventSuffix(USER_TASK_2),
          USER_TASK,
          processInstance
        ),
        createAssertionEvent(
          applyCamundaTaskEndEventSuffix(USER_TASK_2),
          applyCamundaTaskEndEventSuffix(USER_TASK_2),
          USER_TASK,
          processInstance
        ),
        createAssertionEvent(END_EVENT, END_EVENT, END_EVENT, processInstance),
        createAssertionEvent(
          applyCamundaProcessInstanceEndEventSuffix(processInstance.getProcessDefinitionKey()),
          PROCESS_END_TYPE,
          PROCESS_END_TYPE,
          processInstance
        )
      )
      .extracting(CamundaActivityEventDto.Fields.activityInstanceId)
      .contains(applyCamundaProcessInstanceStartEventSuffix(processInstance.getId()));
    assertOrderCounters(storedEvents);
  }

  @Test
  public void noEventsCreatedOnImportWithFeatureDisabled() throws JsonProcessingException {
    // given the index has been created, the process start, the start Event, and start of user task has been saved
    // already
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("noEventsDef");
    importAllEngineEntitiesFromScratch();
    List<CamundaActivityEventDto> initialStoredEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    assertThat(initialStoredEvents)
      .hasSize(3)
      .extracting(CamundaActivityEventDto::getActivityId)
      .containsExactlyInAnyOrder(
        START_EVENT,
        applyCamundaTaskStartEventSuffix(USER_TASK),
        applyCamundaProcessInstanceStartEventSuffix(processInstanceEngineDto.getProcessDefinitionKey())
      );

    // when the feature is disabled
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);

    // when engine events happen and import triggered
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromLastIndex();

    // then no additional events are stored
    List<CamundaActivityEventDto> storedEvents =
      getSavedEventsForProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey());
    assertThat(storedEvents).usingFieldByFieldElementComparator().isEqualTo(initialStoredEvents);
  }

  @Test
  public void expectedIndicesCreatedWithMultipleDefinitionsImportedInSameBatch() throws IOException {
    // given
    ProcessInstanceEngineDto firstProcessInstanceEngineDto =
      deployAndStartUserTaskProcessWithName("firstProcessSameBatch");
    ProcessInstanceEngineDto secondProcessInstanceEngineDto =
      deployAndStartUserTaskProcessWithName("secondProcessSameBatch");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(
      createExpectedIndexNameForProcessDefinition(firstProcessInstanceEngineDto.getProcessDefinitionKey()),
      createExpectedIndexNameForProcessDefinition(secondProcessInstanceEngineDto.getProcessDefinitionKey())
    );
    assertThat(esClient.exists(request, RequestOptions.DEFAULT)).isTrue();

    // then events have been saved in each index
    assertThat(getSavedEventsForProcessDefinitionKey(firstProcessInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(6);
    assertThat(getSavedEventsForProcessDefinitionKey(secondProcessInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(6);
  }

  @Test
  public void expectedIndicesCreatedWithMultipleDefinitionsImportedInMultipleBatches() throws IOException {
    // given
    ProcessInstanceEngineDto firstProcessInstanceEngineDto =
      deployAndStartUserTaskProcessWithName("aProcessFirstBatch");
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    ProcessInstanceEngineDto secondProcessInstanceEngineDto =
      deployAndStartUserTaskProcessWithName("aProcessSecondBatch");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromLastIndex();

    // then
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(
      createExpectedIndexNameForProcessDefinition(firstProcessInstanceEngineDto.getProcessDefinitionKey()),
      createExpectedIndexNameForProcessDefinition(secondProcessInstanceEngineDto.getProcessDefinitionKey())
    );
    assertThat(esClient.exists(request, RequestOptions.DEFAULT)).isTrue();

    // then events have been saved in each index. The conversion to set is to remove duplicate entries due to
    // multiple import batches
    Set<String> idsInFirstIndex =
      getSavedEventsForProcessDefinitionKey(firstProcessInstanceEngineDto.getProcessDefinitionKey())
        .stream()
        .map(CamundaActivityEventDto::getActivityId)
        .collect(Collectors.toSet());
    assertThat(idsInFirstIndex).hasSize(6);
    assertThat(getSavedEventsForProcessDefinitionKey(secondProcessInstanceEngineDto.getProcessDefinitionKey()))
      .hasSize(6);
  }

  @Test
  public void noIndexCreatedOnImportWithFeatureDisabled() throws IOException {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName("shouldNotBeCreated");

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    GetIndexRequest request = new GetIndexRequest(
      createExpectedIndexNameForProcessDefinition(processInstanceEngineDto.getProcessDefinitionKey()));
    assertThat(esClient.exists(request, RequestOptions.DEFAULT)).isFalse();
  }

  private CamundaActivityEventDto createAssertionEvent(final String activityId,
                                                       final String activityName,
                                                       final String activityType,
                                                       final ProcessInstanceEngineDto processInstanceEngineDto) {
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
    return new CamundaActivityEventIndex(processDefinitionKey).getIndexName();
  }

  private List<CamundaActivityEventDto> getSavedEventsForProcessDefinitionKey(final String processDefinitionKey) throws
                                                                                                                 JsonProcessingException {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      new CamundaActivityEventIndex(processDefinitionKey).getIndexName()
    );
    List<CamundaActivityEventDto> storedEvents = new ArrayList<>();
    for (SearchHit searchHitFields : response.getHits()) {
      final CamundaActivityEventDto camundaActivityEventDto = embeddedOptimizeExtension.getObjectMapper().readValue(
        searchHitFields.getSourceAsString(), CamundaActivityEventDto.class);
      storedEvents.add(camundaActivityEventDto);
    }
    return storedEvents;
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithName(String processName) {
    return engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(
      processName,
      START_EVENT,
      END_EVENT,
      USER_TASK
    ));
  }

  protected ProcessInstanceEngineDto deployAndStartTwoUserTasksProcess(String processName) {
    return engineIntegrationExtension.deployAndStartProcess(getDoubleUserTaskDiagram(
      processName,
      START_EVENT,
      END_EVENT,
      USER_TASK,
      USER_TASK_2
    ));
  }

  private void assertOrderCounters(final List<CamundaActivityEventDto> storedEvents) {
    final List<CamundaActivityEventDto> orderedEvents = storedEvents.stream()
      .filter(event -> !event.getActivityType()
        .equalsIgnoreCase(PROCESS_START_TYPE) && !event.getActivityType()
        .equalsIgnoreCase(PROCESS_END_TYPE))
      .collect(Collectors.toList());
    assertThat(orderedEvents)
      .extracting(CamundaActivityEventDto::getOrderCounter)
      .doesNotContainNull();
  }

}
