/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.SneakyThrows;
import org.assertj.core.util.Maps;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> secondImportProcessInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
  public void instancesAreGeneratedFromCamundaEventImportSource_correlatedByVariable_variableNotFound() {
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances).isEmpty();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
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
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances).isEmpty();
  }

  private ProcessInstanceEngineDto deployAndStartProcess() {
    return deployAndStartProcessWithVariables(Collections.emptyMap());
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
