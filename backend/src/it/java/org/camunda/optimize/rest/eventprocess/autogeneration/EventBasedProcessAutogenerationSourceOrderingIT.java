/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess.autogeneration;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;

// The tests in this class relate to the order in which the events from each source appear in the generated model
public class EventBasedProcessAutogenerationSourceOrderingIT extends AbstractEventProcessAutogenerationIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getEventImport()
      .setEnabled(true);
  }

  @Test
  public void createFromTwoCamundaSources_oneSourceHasNoInstance() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    final CamundaEventSourceEntryDto secondCamundaSource =
      createCamundaSourceEntryForImportedDefinition("processWithInstance");

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final Response response = eventProcessClient.createCreateEventProcessMappingRequest(createRequestDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createFromTwoCamundaSources_bothSourcesHaveNoInstance() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END
    );

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final Response response = eventProcessClient.createCreateEventProcessMappingRequest(createRequestDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createFromTwoCamundaSources_instancesFromOtherDefinitionsDoNotAffectResult() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final String businessKey = "businessKey1";
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END,
      businessKey
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END,
      businessKey
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    // This instance is for a definition not included as part of the source
    BpmnModelInstance thirdModelInstance = singleStartSingleEndModel(PROCESS_ID_3, START_EVENT_ID_3, END_EVENT_ID_3);
    deployDefinitionAndStartInstance(thirdModelInstance);

    // We supply the second source before the first, even though the instance started after, to make sure
    // the order determination is being applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(secondCamundaSource, firstCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);
  }

  @Test
  public void createFromTwoCamundaSources_onlyCompletedInstancesConsidered() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndUserTaskModel(
      PROCESS_ID_1,
      START_EVENT_ID_1,
      END_EVENT_ID_1
    );
    final String businessKey = "businessKey1";
    // the instance started here is not completed as the user task is not finished
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END,
      businessKey
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END,
      businessKey
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the second source comes first because we have a completed instance of it, the incomplete instance is ignored
    assertNodeConnection(idOf(secondStart), START_EVENT, idOf(secondEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), INTERMEDIATE_EVENT, idOf(firstStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstStart), INTERMEDIATE_EVENT, idOf(firstEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);
  }

  @Test
  public void createFromTwoCamundaSources_onlyInstancesOfGivenVersionConsidered() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndUserTaskModel(
      PROCESS_ID_1,
      START_EVENT_ID_1,
      END_EVENT_ID_1
    );
    final String businessKey = "businessKey1";

    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    // this instance started here will not be considered as it is for a different version than used in the source
    deployDefinitionAndStartInstance(firstModelInstance);

    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END,
      businessKey
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the second source comes first because we have relevant instance of it, the instance for the first source
    // is ignored as it is for a different version
    assertNodeConnection(idOf(secondStart), START_EVENT, idOf(secondEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), INTERMEDIATE_EVENT, idOf(firstStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstStart), INTERMEDIATE_EVENT, idOf(firstEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);
  }

  @Test
  public void createFromTwoCamundaSources_onlyInstancesOfGivenTenantConsidered() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndUserTaskModel(
      PROCESS_ID_1,
      START_EVENT_ID_1,
      END_EVENT_ID_1
    );
    final String businessKey = "businessKey1";

    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    // this instance started here will not be considered as it is for a different tenant than used in the source
    deployDefinitionWithTenantAndStartInstance(firstModelInstance, "someTenantId");

    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END,
      businessKey
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping = autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the second source comes first because we have relevant instance of it, the instance for the first source
    // is ignored as it is for a different tenant
    assertNodeConnection(idOf(secondStart), START_EVENT, idOf(secondEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), INTERMEDIATE_EVENT, idOf(firstStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstStart), INTERMEDIATE_EVENT, idOf(firstEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);
  }

  private void deployDefinitionAndStartInstance(final BpmnModelInstance firstModelInstance) {
    deployDefinitionWithTenantAndStartInstance(firstModelInstance, null);
    importAllEngineEntitiesFromLastIndex();
  }

  private void deployDefinitionWithTenantAndStartInstance(final BpmnModelInstance firstModelInstance,
                                                          final String tenantId) {
    final ProcessDefinitionEngineDto firstDefinitionSecondVersion = deployDefinition(firstModelInstance, tenantId);
    engineIntegrationExtension.startProcessInstance(
      firstDefinitionSecondVersion.getId(),
      ImmutableMap.of(DEFAULT_VARIABLE, true, STRING_VAR, STRING_VAR_VAL)
    );
  }

}
