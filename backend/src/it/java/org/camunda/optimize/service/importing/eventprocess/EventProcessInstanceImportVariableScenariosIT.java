/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import org.assertj.core.util.Maps;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;

public class EventProcessInstanceImportVariableScenariosIT extends AbstractEventProcessIT {

  @Test
  public void instancesAreGeneratedWithCorrectVariablesFromCamundaEventImportSource_tracedByBusinessKey() {
    // given
    Map<VariableType, Map<String, Object>> variableTypeToVariableMap = createVariableTypeToVariableMap();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessWithVariables(
      convertToVariables(variableTypeToVariableMap));
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
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
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID)
        );
        assertEngineVariables(processInstanceDto, variableTypeToVariableMap);
      });
  }

  @Test
  public void instancesAreGeneratedWithCorrectVariablesFromCamundaEventImportSource_multipleImportBatches() {
    // given
    Map<VariableType, Map<String, Object>> variableTypeToVariableMap = createVariableTypeToVariableMap();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessWithVariables(
      convertToVariables(variableTypeToVariableMap));
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
        BPMN_END_EVENT_ID
      )
    );
    importEngineEntities();

    // when
    executeImportCycle();

    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID)
        );
        assertEngineVariables(processInstanceDto, variableTypeToVariableMap);
      });
  }

  @Test
  public void instancesAreGeneratedWithCorrectVariablesFromCamundaEventImportSource_tracedByVariable() {
    // given
    Map<VariableType, Map<String, Object>> variableTypeToVariableMap = createVariableTypeToVariableMap();
    Map.Entry<String, Object> tracingVariableKeyAndValue =
      variableTypeToVariableMap.get(VariableType.STRING).entrySet().stream().findFirst().get();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessWithVariables(
      convertToVariables(variableTypeToVariableMap));
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
        BPMN_END_EVENT_ID
      ),
      tracingVariableKeyAndValue.getKey()
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          tracingVariableKeyAndValue.getValue().toString(),
          Arrays.asList(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID)
        );
        assertEngineVariables(processInstanceDto, variableTypeToVariableMap);
      });
  }

  @Test
  public void instancesAreGeneratedWithCorrectVariables_camundaAndExternalEventImportSource() {
    // given
    Map<VariableType, Map<String, Object>> variableTypeToVariableMap = createVariableTypeToVariableMap();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessWithVariables(
      convertToVariables(variableTypeToVariableMap));
    importEngineEntities();

    ingestTestEvent(BPMN_END_EVENT_ID, processInstanceEngineDto.getBusinessKey());

    final Map<String, EventMappingDto> mappingsForEventProcess = createMappingsForEventProcess(
      processInstanceEngineDto,
      BPMN_START_EVENT_ID,
      applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
      BPMN_END_EVENT_ID
    );
    mappingsForEventProcess.put(BPMN_END_EVENT_ID, EventMappingDto.builder()
      .start(EventTypeDto.builder()
               .eventName(BPMN_END_EVENT_ID)
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .build())
      .build());

    CamundaEventSourceEntryDto camundaEventSource =
      createCamundaEventSourceEntryForDeployedProcessTracedByBusinessKey(processInstanceEngineDto);
    ExternalEventSourceEntryDto externalEventSource = createExternalEventSource();

    createAndPublishEventMapping(mappingsForEventProcess, Arrays.asList(camundaEventSource, externalEventSource));

    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertProcessInstance(
          processInstanceDto,
          processInstanceEngineDto.getBusinessKey(),
          Arrays.asList(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID)
        );
        List<SimpleProcessVariableDto> expectedVariabes = extractExpectedEngineVariables(variableTypeToVariableMap);
        expectedVariabes.add(expectedExternalEventVariable());
        assertThat(processInstanceDto.getVariables()).containsExactlyInAnyOrderElementsOf(expectedVariabes);
      });
  }

  private SimpleProcessVariableDto expectedExternalEventVariable() {
    return new SimpleProcessVariableDto(
      VARIABLE_ID, VARIABLE_ID, VARIABLE_VALUE.getClass().getSimpleName(), Collections.singletonList(VARIABLE_VALUE), 1
    );
  }

  private Map<String, Object> convertToVariables(final Map<VariableType, Map<String, Object>> variableTypeToVariableMap) {
    return variableTypeToVariableMap.values()
      .stream()
      .flatMap(map -> map.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void assertEngineVariables(final ProcessInstanceDto processInstanceDto,
                                     final Map<VariableType, Map<String, Object>> variableTypeToVariableMap) {
    assertVariables(processInstanceDto, extractExpectedEngineVariables(variableTypeToVariableMap));
  }

  private void assertVariables(ProcessInstanceDto processInstanceDto,
                               List<SimpleProcessVariableDto> expectVariableArray) {
    assertThat(processInstanceDto.getVariables())
      .containsExactlyInAnyOrderElementsOf(expectVariableArray);
  }

  private List<SimpleProcessVariableDto> extractExpectedEngineVariables(final Map<VariableType, Map<String, Object>> variableTypeToVariableMap) {
    return variableTypeToVariableMap.entrySet().stream()
      .flatMap(entry -> entry.getValue().entrySet().stream()
        .map(variableProperties ->
               new SimpleProcessVariableDto(variableProperties.getKey(), variableProperties.getKey(),
                                            entry.getKey().getId(),
                                            Collections.singletonList(variableProperties.getValue().toString()), 1
               )))
      .collect(Collectors.toList());
  }

  private Map<VariableType, Map<String, Object>> createVariableTypeToVariableMap() {
    Map<VariableType, Map<String, Object>> variableTypeToVariableMap = new HashMap<>();
    variableTypeToVariableMap.put(VariableType.STRING, Maps.newHashMap("string key", "some string value"));
    variableTypeToVariableMap.put(VariableType.INTEGER, Maps.newHashMap("integer key", 5));
    variableTypeToVariableMap.put(VariableType.LONG, Maps.newHashMap("long key", 5L));
    short s = 10;
    variableTypeToVariableMap.put(VariableType.SHORT, Maps.newHashMap("short key", s));
    variableTypeToVariableMap.put(VariableType.DOUBLE, Maps.newHashMap("double key", 5.5d));
    variableTypeToVariableMap.put(VariableType.BOOLEAN, Maps.newHashMap("boolean key", true));
    variableTypeToVariableMap.put(VariableType.DATE, Maps.newHashMap("date key", new Date()));
    return variableTypeToVariableMap;
  }

}
