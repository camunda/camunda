/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess.autogeneration;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateConnectionGatewayIdForDefinitionKey;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateModelGatewayIdForSource;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;

// The tests in this class relate to the connections between sources
public class EventBasedProcessAutogenerationSourceConnectionsIT extends AbstractEventProcessAutogenerationIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getEventImport()
      .setEnabled(true);
  }

  @Test
  public void createFromTwoCamundaStartEndEventSources_singleStartEndEvents() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

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

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(3);
  }

  @Test
  public void createFromTwoCamundaStartEndEventSources_multipleStartEndEvents() {
    BpmnModelInstance firstModelInstance = multipleStartMultipleEndModel(PROCESS_ID_1);
    final CamundaEventSourceEntryDto firstCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto firstStart1 = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto firstStart2 = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto firstEnd1 = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);
    final EventTypeDto firstEnd2 = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_2, END_EVENT_ID_2);

    BpmnModelInstance secondModelInstance = multipleStartMultipleEndModel(PROCESS_ID_2);
    final CamundaEventSourceEntryDto secondCamundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto secondStart1 = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto secondStart2 = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd1 = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_1, END_EVENT_ID_1);
    final EventTypeDto secondEnd2 = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(firstCamundaSource, secondCamundaSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart1, firstStart2, firstEnd1, firstEnd2, secondStart1, secondStart2, secondEnd1, secondEnd2)
    );

    // 4 additional gateways exist in the source models, plus a single gateway to connect two source models
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 6);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String convergingId1 = generateModelGatewayIdForSource(firstCamundaSource, Converging);
    final String divergingId1 = generateModelGatewayIdForSource(firstCamundaSource, Diverging);
    final String convergingId2 = generateModelGatewayIdForSource(secondCamundaSource, Converging);
    final String divergingId2 = generateModelGatewayIdForSource(secondCamundaSource, Diverging);
    // The previous source is responsible for creating the converging connecting gateway so the ID uses its
    // definition key
    final String connectingId1 = generateConnectionGatewayIdForDefinitionKey(
      Converging,
      firstCamundaSource.getConfiguration().getProcessDefinitionKey()
    );
    // The next source is responsible for creating the diverging connecting gateway so the ID uses its definition key
    final String connectingId2 = generateConnectionGatewayIdForDefinitionKey(
      Diverging,
      secondCamundaSource.getConfiguration().getProcessDefinitionKey()
    );
    assertNodeConnection(idOf(firstStart1), START_EVENT, convergingId1, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(firstStart2), START_EVENT, convergingId1, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(convergingId1, EXCLUSIVE_GATEWAY, divergingId1, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(divergingId1, EXCLUSIVE_GATEWAY, idOf(firstEnd1), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(divergingId1, EXCLUSIVE_GATEWAY, idOf(firstEnd2), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd1), INTERMEDIATE_EVENT, connectingId1, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(firstEnd2), INTERMEDIATE_EVENT, connectingId1, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(connectingId1, EXCLUSIVE_GATEWAY, connectingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(connectingId2, EXCLUSIVE_GATEWAY, idOf(secondStart1), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(connectingId2, EXCLUSIVE_GATEWAY, idOf(secondStart2), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart1), INTERMEDIATE_EVENT, convergingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(secondStart2), INTERMEDIATE_EVENT, convergingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(convergingId2, EXCLUSIVE_GATEWAY, divergingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(divergingId2, EXCLUSIVE_GATEWAY, idOf(secondEnd1), END_EVENT, generatedInstance);
    assertNodeConnection(divergingId2, EXCLUSIVE_GATEWAY, idOf(secondEnd2), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd1), END_EVENT, null, null, generatedInstance);
    assertNodeConnection(idOf(secondEnd2), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(15);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(6);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(firstStart1), idOf(firstStart2)),
      Arrays.asList(divergingId1),
      getGatewayWithId(gatewaysInModel, convergingId1)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(convergingId1),
      Arrays.asList(idOf(firstEnd1), idOf(firstEnd2)),
      getGatewayWithId(gatewaysInModel, divergingId1)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(firstEnd1), idOf(firstEnd2)),
      Arrays.asList(connectingId2),
      getGatewayWithId(gatewaysInModel, connectingId1)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(connectingId1),
      Arrays.asList(idOf(secondStart1), idOf(secondStart2)),
      getGatewayWithId(gatewaysInModel, connectingId2)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(secondStart1), idOf(secondStart2)),
      Arrays.asList(divergingId2),
      getGatewayWithId(gatewaysInModel, convergingId2)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(convergingId2),
      Arrays.asList(idOf(secondEnd1), idOf(secondEnd2)),
      getGatewayWithId(gatewaysInModel, divergingId2)
    );
  }

  @Test
  public void createFromTwoCamundaSources_camundaSourceHasNoEndEvents_canStillConnectToNextSource() {
    BpmnModelInstance firstModelInstance = singleStartNoEndModel();
    final CamundaEventSourceEntryDto noEndEventSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);

    BpmnModelInstance secondModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto endEventSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto secondEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(noEndEventSource, endEventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart, secondStart, secondEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, idOf(secondEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(secondEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(2);
  }

  @Test
  public void createFromTwoCamundaSources_camundaSourceHasNoEndEvents_canStillExistAtEndOfGeneratedModel() {
    BpmnModelInstance firstModelInstance = singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2);
    final CamundaEventSourceEntryDto endEventSource = deployDefinitionWithInstanceAndCreateEventSource(
      firstModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto firstStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto firstEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    BpmnModelInstance secondModelInstance = singleStartNoEndModel();
    final CamundaEventSourceEntryDto noEndEventSource = deployDefinitionWithInstanceAndCreateEventSource(
      secondModelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto secondStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(endEventSource, noEndEventSource);
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertCorrectMappingsAndContainsEvents(
      mappings,
      generatedInstance,
      Arrays.asList(firstStart, firstEnd, secondStart)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(firstStart), START_EVENT, idOf(firstEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(firstEnd), INTERMEDIATE_EVENT, idOf(secondStart), INTERMEDIATE_EVENT, generatedInstance);
    // The model is still valid, but it will not contain an end event, just like its source
    assertNodeConnection(idOf(secondStart), INTERMEDIATE_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(2);
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

}
