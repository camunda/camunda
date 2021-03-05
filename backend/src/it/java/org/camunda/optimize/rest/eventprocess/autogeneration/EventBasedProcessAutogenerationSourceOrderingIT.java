/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;

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
  public void createFromTwoCamundaSources_bothSourcesWithInstancesWithNoCommonCorrelatingValue() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END,
      "businessKey1"
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END,
      "businessKey2"
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

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

    // then the model elements are sequenced by source in order that they were supplied
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);
  }

  @Test
  public void createFromTwoCamundaSources_bothSourcesWithInstancesWithCorrelatingValue() {
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

  @Test
  public void createFromMultipleCamundaSources_correlationValueNotPresentInInstancesOfAllSources_defaultEndPlacing() {
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

    BpmnModelInstance thirdModelInstance = singleStartSingleEndModel(PROCESS_ID_3, START_EVENT_ID_3, END_EVENT_ID_3);
    // This uses a different business key so can't be correlated by instance and should be placed at the end
    final CamundaEventSourceEntryDto thirdCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      thirdModelInstance,
      EventScopeType.START_END,
      "someOtherBusinessKey"
    );
    final EventTypeDto thirdStart = createCamundaEventTypeDto(PROCESS_ID_3, START_EVENT_ID_3, START_EVENT_ID_3);
    final EventTypeDto thirdEnd = createCamundaEventTypeDto(PROCESS_ID_3, END_EVENT_ID_3, END_EVENT_ID_3);

    // We supply the sources in an order different to our expectations to test that order determination is applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      thirdCamundaSource,
      secondCamundaSource,
      firstCamundaSource
    );
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
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd, thirdStart, thirdEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value, and the uncorrelatable source is at the default end position
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), INTERMEDIATE_EVENT, idOf(thirdStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(thirdStart), INTERMEDIATE_EVENT, idOf(thirdEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(thirdEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  @Test
  public void createFromMultipleCamundaSources_correlationValueNotAlwaysPresent_useSecondaryPlacingBeforePlacedSource() {
    // the first and second sources have instances that can be correlated together by business key
    final String firstBusinessKey = "businessKey1";
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END,
      firstBusinessKey
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final ProcessInstanceEngineDto secondInstanceEngineDto =
      deployDefinitionWithInstance(secondModelInstance, Collections.emptyMap(), firstBusinessKey);
    final CamundaEventSourceEntryDto secondCamundaSource = createCamundaSourceEntry(
      secondInstanceEngineDto.getProcessDefinitionKey(),
      EventScopeType.START_END
    );

    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    // the second and third sources have instances that can be correlated together by another business key
    final String secondBusinessKey = "businessKey2";
    BpmnModelInstance thirdModelInstance = singleStartSingleEndModel(PROCESS_ID_3, START_EVENT_ID_3, END_EVENT_ID_3);
    final CamundaEventSourceEntryDto thirdCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      thirdModelInstance,
      EventScopeType.START_END,
      secondBusinessKey
    );
    final EventTypeDto thirdStart = createCamundaEventTypeDto(PROCESS_ID_3, START_EVENT_ID_3, START_EVENT_ID_3);
    final EventTypeDto thirdEnd = createCamundaEventTypeDto(PROCESS_ID_3, END_EVENT_ID_3, END_EVENT_ID_3);

    engineIntegrationExtension.startProcessInstance(
      secondInstanceEngineDto.getDefinitionId(),
      Collections.emptyMap(),
      secondBusinessKey
    );
    importAllEngineEntitiesFromLastIndex();

    // We supply the sources in an order different to our expectations to test that order determination is applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      thirdCamundaSource,
      secondCamundaSource,
      firstCamundaSource
    );
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
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd, thirdStart, thirdEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value across all correlatable instance
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(thirdStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(thirdStart), INTERMEDIATE_EVENT, idOf(thirdEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(thirdEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  @Test
  public void createFromMultipleCamundaSources_correlationValueNotAlwaysPresent_useSecondaryPlacingAfterPlacedSource() {
    // the first and second sources have instances that can be correlated together by business key
    final String firstBusinessKey = "businessKey1";
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final ProcessInstanceEngineDto firstInstanceEngineDto =
      deployDefinitionWithInstance(firstModelInstance, Collections.emptyMap(), firstBusinessKey);
    final CamundaEventSourceEntryDto firstCamundaSource = createCamundaSourceEntry(
      firstInstanceEngineDto.getProcessDefinitionKey(),
      EventScopeType.START_END
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END,
      firstBusinessKey
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    // the first and third sources have instances that can be correlated together by another business key
    final String secondBusinessKey = "businessKey2";
    engineIntegrationExtension.startProcessInstance(
      firstInstanceEngineDto.getDefinitionId(),
      Collections.emptyMap(),
      secondBusinessKey
    );
    importAllEngineEntitiesFromLastIndex();

    BpmnModelInstance thirdModelInstance = singleStartSingleEndModel(PROCESS_ID_3, START_EVENT_ID_3, END_EVENT_ID_3);
    final CamundaEventSourceEntryDto thirdCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      thirdModelInstance,
      EventScopeType.START_END,
      secondBusinessKey
    );
    final EventTypeDto thirdStart = createCamundaEventTypeDto(PROCESS_ID_3, START_EVENT_ID_3, START_EVENT_ID_3);
    final EventTypeDto thirdEnd = createCamundaEventTypeDto(PROCESS_ID_3, END_EVENT_ID_3, END_EVENT_ID_3);

    // We supply the sources in an order different to our expectations to test that order determination is applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      thirdCamundaSource,
      secondCamundaSource,
      firstCamundaSource
    );
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
      Arrays.asList(firstStart, firstEnd, secondStart, secondEnd, thirdStart, thirdEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value across all correlatable instance
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(thirdStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(thirdStart), INTERMEDIATE_EVENT, idOf(thirdEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(thirdEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  @Test
  public void createFromCamundaAndExternalSources_correlatableInstanceForAllSources() {
    final String businessKey = "businessKey1";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, businessKey, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_B, businessKey, now)
    ));
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
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

    // We supply the sources in a different order than we expect them to appear, to make sure the order determination
    // is being applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      secondCamundaSource,
      firstCamundaSource,
      createExternalEventAllGroupsSourceEntry()
    );
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
      Arrays.asList(EVENT_A, EVENT_B, firstStart, firstEnd, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(firstStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstStart), INTERMEDIATE_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  @Test
  public void createFromTwoCamundaAndExternalSources_externalSourceNotCorrelatable() {
    final String businessKey = "businessKey1";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, "nonCorrelatedKey", now.minusSeconds(10)),
      createCloudEventOfType(EVENT_B, "nonCorrelatedKey", now)
    ));
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
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

    // We supply the sources in a different order than we expect them to appear, to make sure the order determination
    // is being applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      secondCamundaSource,
      createExternalEventAllGroupsSourceEntry(),
      firstCamundaSource
    );
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
      Arrays.asList(EVENT_A, EVENT_B, firstStart, firstEnd, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value. The external events are at the end because they could not be correlated
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), INTERMEDIATE_EVENT, idOf(EVENT_A), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_A), INTERMEDIATE_EVENT, idOf(EVENT_B), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  @Test
  public void createFromTwoCamundaAndExternalSources_useSecondaryPlacingForExternalSource() {
    // the first and second Camunda sources have instances that can be correlated together by business key
    final String firstBusinessKey = "businessKey1";
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END,
      firstBusinessKey
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final ProcessInstanceEngineDto secondInstanceEngineDto =
      deployDefinitionWithInstance(secondModelInstance, Collections.emptyMap(), firstBusinessKey);
    final CamundaEventSourceEntryDto secondCamundaSource = createCamundaSourceEntry(
      secondInstanceEngineDto.getProcessDefinitionKey(),
      EventScopeType.START_END
    );

    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    // the external source and second Camunda source have instances that can be correlated together by another
    // business key
    final String secondBusinessKey = "businessKey2";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondBusinessKey, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_B, secondBusinessKey, now)
    ));

    engineIntegrationExtension.startProcessInstance(
      secondInstanceEngineDto.getDefinitionId(),
      Collections.emptyMap(),
      secondBusinessKey
    );
    importAllEngineEntitiesFromLastIndex();

    // We supply the sources in an order different to our expectations to test that order determination is applied
    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      secondCamundaSource,
      firstCamundaSource
    );
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
      Arrays.asList(firstStart, firstEnd, EVENT_A, EVENT_B, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are sequenced by source in order of the start date of the instance of the correlated
    // value across all correlatable instance
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(EVENT_A), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_A), INTERMEDIATE_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  private void deployDefinitionAndStartInstance(final BpmnModelInstance firstModelInstance) {
    deployDefinitionWithTenantAndStartInstance(firstModelInstance, null);
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
