/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.SneakyThrows;
import org.assertj.core.util.Maps;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler.RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

public class EventProcessInstanceImportImportSourceScenariosIT extends AbstractEventProcessIT {

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void multipleInstancesAreGeneratedFromCamundaEventImportSource() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(2)
      .allSatisfy(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_allEvents_multipleBatches() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID)
        );
      });

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> secondImportProcessInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(secondImportProcessInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_processStartEndEvents() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        applyCamundaProcessInstanceStartEventSuffix(processInstanceEngineDto.getProcessDefinitionKey()),
        BPMN_INTERMEDIATE_EVENT_ID,
        applyCamundaProcessInstanceEndEventSuffix(processInstanceEngineDto.getProcessDefinitionKey())
      )
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_correlatedByVariable() {
    // given
    final String tracingVariable = "tracingVariable";
    final String variableValue = "someValue";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessWithVariables(Maps.newHashMap(
      tracingVariable,
      variableValue
    ));
    publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      ),
      tracingVariable
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          variableValue,
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_correlatedByVariable_variableNotFoundAmongImportedVariables() {
    // given
    final String otherVariable = "variableForProcessInstance";
    final String otherVariableValue = "someValue";
    final String tracingVariable = "tracingVariableNotUsedIntoProcess";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessWithVariables(Maps.newHashMap(
      otherVariable,
      otherVariableValue
    ));
    publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      ),
      tracingVariable
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances).isEmpty();
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_correlatedByVariable_noVariablesImported() {
    // given
    final String tracingVariable = "tracingVariableNotUsedIntoProcess";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      ),
      tracingVariable
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances).isEmpty();
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_correlatedByBusinessKey_businessKeyNotFound() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    deleteBusinessKeyFromElasticsearchForProcessInstance(processInstanceEngineDto.getId());

    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances).isEmpty();
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_correlatedByBusinessKey_ignoreInstancesWithNullBusinessKey() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceWithBusinessKey(null);
    final ProcessInstanceEngineDto instanceWithBusinessKey = engineIntegrationExtension
      .startProcessInstance(processInstanceEngineDto.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );

    // when
    executeImportCycle();

    // then only the instance with a business key present is saved
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          instanceWithBusinessKey.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromMultipleCamundaEventImportSources() {
    // given
    final ProcessInstanceEngineDto firstProcessInstanceEngineDto = deployAndStartProcess();
    final ProcessInstanceEngineDto secondProcessInstanceEngineDto = deployAndStartProcess();

    final Map<String, EventMappingDto> mappingsForEventProcess = createMappingsForEventProcess(
      firstProcessInstanceEngineDto,
      BPMN_START_EVENT_ID,
      applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
      BPMN_END_EVENT_ID
    );
    mappingsForEventProcess.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder()
        .start(EventTypeDto.builder()
                 .eventName(BPMN_END_EVENT_ID)
                 .group(secondProcessInstanceEngineDto.getProcessDefinitionKey())
                 .source(EVENT_SOURCE_CAMUNDA)
                 .build()
        )
        .build()
    );

    List<EventSourceEntryDto> firstEventSource = createCamundaEventSourceEntryForDeployedProcessWithBusinessKey(
      firstProcessInstanceEngineDto);
    List<EventSourceEntryDto> secondEventSource = createCamundaEventSourceEntryForDeployedProcessWithBusinessKey(
      secondProcessInstanceEngineDto);

    createAndPublishEventMapping(mappingsForEventProcess, Stream.of(firstEventSource, secondEventSource).flatMap(
      Collection::stream).collect(Collectors.toList()));

    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          secondProcessInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromExternalAndCamundaEventImportSources() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    ingestTestEvent(BPMN_END_EVENT_ID, processInstanceEngineDto.getBusinessKey());

    final Map<String, EventMappingDto> mappingsForEventProcess = createMappingsForEventProcess(
      processInstanceEngineDto,
      BPMN_START_EVENT_ID,
      applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
      BPMN_END_EVENT_ID
    );
    mappingsForEventProcess.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder()
        .start(EventTypeDto.builder()
                 .eventName(BPMN_END_EVENT_ID)
                 .group(EXTERNAL_EVENT_GROUP)
                 .source(EXTERNAL_EVENT_SOURCE)
                 .build())
        .build()
    );

    List<EventSourceEntryDto> firstEventSource = createCamundaEventSourceEntryForDeployedProcessWithBusinessKey(
      processInstanceEngineDto);
    List<EventSourceEntryDto> secondEventSource = createExternalEventSource();

    createAndPublishEventMapping(mappingsForEventProcess, Stream.of(firstEventSource, secondEventSource).flatMap(
      Collection::stream).collect(Collectors.toList()));

    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID, BPMN_END_EVENT_ID)
        );
      });
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_ignoreEventsWithVersionNotMatching() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    final List<EventSourceEntryDto> eventSource =
      createCamundaEventSourceEntryForDeployedProcessWithVersions(
        processInstanceEngineDto,
        Collections.singletonList("versionNotSameAsInstance")
      );

    final Map<String, EventMappingDto> mappingsForEventProcess = createMappingsForEventProcess(
      processInstanceEngineDto,
      BPMN_START_EVENT_ID,
      applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
      BPMN_END_EVENT_ID
    );
    createAndPublishEventMapping(mappingsForEventProcess, eventSource);

    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances).isEmpty();
  }

  @Test
  public void instancesAreGeneratedFromCamundaEventImportSource_eventsNewerThanLastExecutionTimestampOfImportersNotIncluded() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(BPMN_INTERMEDIATE_EVENT_ID),
        BPMN_END_EVENT_ID
      )
    );

    importEngineEntities();
    CamundaActivityEventDto lastImportedActivityForFirstImport =
      getLastImportedActivityForProcessDefinition(processInstanceEngineDto.getProcessDefinitionKey());

    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    updateImportIndexLastImportExecutionTimestamp(
      RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
      lastImportedActivityForFirstImport.getTimestamp().plus(1, ChronoField.MILLI_OF_SECOND.getBaseUnit())
    );

    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasOnlyOneElementSatisfying(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, BPMN_INTERMEDIATE_EVENT_ID)
        );
      });
  }

  private void updateImportIndexLastImportExecutionTimestamp(final String importType,
                                                             final OffsetDateTime timestampToSet) {
    final TimestampBasedImportIndexDto runningProcessImport = new ArrayList<>(ElasticsearchHelper.mapHits(
      elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
        TIMESTAMP_BASED_IMPORT_INDEX_NAME).getHits(),
      TimestampBasedImportIndexDto.class,
      embeddedOptimizeExtension.getObjectMapper()
    )).stream().filter(index -> index.getEsTypeIndexRefersTo().equalsIgnoreCase(
      importType))
      .findFirst().get();
    runningProcessImport.setLastImportExecutionTimestamp(timestampToSet);

    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      TIMESTAMP_BASED_IMPORT_INDEX_NAME,
      EsHelper.constructKey(
        runningProcessImport.getEsTypeIndexRefersTo(),
        runningProcessImport.getEngine()
      ),
      runningProcessImport
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private CamundaActivityEventDto getLastImportedActivityForProcessDefinition(final String processDefinitionKey) {
    return ElasticsearchHelper.mapHits(
      elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
        new CamundaActivityEventIndex(processDefinitionKey).getIndexName()
      ).getHits(),
      CamundaActivityEventDto.class,
      embeddedOptimizeExtension.getObjectMapper()
    ).stream().max(Comparator.comparing(CamundaActivityEventDto::getTimestamp)).get();
  }

  private ProcessInstanceEngineDto deployAndStartProcess() {
    return deployAndStartProcessWithVariables(Collections.emptyMap());
  }

  private ProcessInstanceEngineDto deployAndStartInstanceWithBusinessKey(String businessKey) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      Bpmn.createExecutableProcess("aProcess")
        .startEvent(BPMN_START_EVENT_ID)
        .userTask(BPMN_INTERMEDIATE_EVENT_ID)
        .endEvent(BPMN_END_EVENT_ID)
        .done(),
      Collections.emptyMap(), businessKey, null
    );
  }

  private List<EventSourceEntryDto> createCamundaEventSourceEntryForDeployedProcessWithVersions(final ProcessInstanceEngineDto processInstanceEngineDto,
                                                                                                final List<String> versions) {
    return createCamundaEventSourceEntryForDeployedProcess(processInstanceEngineDto, null, versions);
  }

  @SneakyThrows
  public void deleteBusinessKeyFromElasticsearchForProcessInstance(String processInstanceId) {
    DeleteRequest request =
      new DeleteRequest(BUSINESS_KEY_INDEX_NAME)
        .id(processInstanceId)
        .setRefreshPolicy(IMMEDIATE);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().delete(request, RequestOptions.DEFAULT);
  }

}
