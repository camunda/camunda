/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess.autogeneration;

import com.google.common.collect.ImmutableMap;
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
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Converging;
import static org.camunda.bpm.model.bpmn.GatewayDirection.Diverging;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessEndEventTypeDto;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaProcessStartEventTypeDto;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateConnectionGatewayIdForDefinitionKey;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateGatewayIdForNode;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateModelGatewayIdForSource;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateNodeId;
import static org.camunda.optimize.service.util.EventModelBuilderUtil.generateTaskIdForDefinitionKey;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public class EventBasedProcessAutogenerationMultipleSourceConnectionsIT extends AbstractEventProcessAutogenerationIT {

  @Test
  public void createFromExternalAndCamundaSources_noExternalEndEvents_singleCamundaStartEvent() {
    final Instant now = Instant.now();
    final String firstTraceId = "firstTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(10))
    ));
    final String secondTraceId = "secondTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_B, secondTraceId, now),
      createCloudEventOfType(EVENT_A, secondTraceId, now.plusSeconds(10))
    ));

    BpmnModelInstance modelInstance = singleStartSingleEndModel();
    final CamundaEventSourceEntryDto camundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto camundaStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      camundaSource
    );
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
      Arrays.asList(EVENT_A, EVENT_B, camundaStart, camundaEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    // We cannot assert on the actual connection between the models as we don't know which start event will be connected
    // to the camunda start events
    assertNodeConnection(idOf(camundaStart), INTERMEDIATE_EVENT, idOf(camundaEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(2);
  }

  @Test
  public void createFromExternalAndCamundaSources_noExternalEndEvents_multipleCamundaStartEvent() {
    final Instant now = Instant.now();
    final String firstTraceId = "firstTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now),
      createCloudEventOfType(EVENT_B, firstTraceId, now.plusSeconds(10))
    ));
    final String secondTraceId = "secondTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_B, secondTraceId, now),
      createCloudEventOfType(EVENT_A, secondTraceId, now.plusSeconds(10))
    ));

    BpmnModelInstance modelInstance = multipleStartSingleEndModel();
    final CamundaEventSourceEntryDto camundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto camundaStart1 = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto camundaStart2 = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      camundaSource
    );
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
      Arrays.asList(EVENT_A, EVENT_B, camundaStart1, camundaStart2, camundaEnd)
    );
    // The additional Flow nodes are the gateway from the Camunda source and the diverging connecting gateway
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 2);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String convergingId = generateModelGatewayIdForSource(camundaSource, Converging);
    final String connectingId = generateConnectionGatewayIdForDefinitionKey(
      Diverging,
      camundaSource.getConfiguration().getProcessDefinitionKey()
    );
    // We cannot assert on the actual connections between the models as we don't know which start event will be
    // connected to the camunda start events
    assertNodeConnection(connectingId, EXCLUSIVE_GATEWAY, idOf(camundaStart1), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(connectingId, EXCLUSIVE_GATEWAY, idOf(camundaStart2), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaStart1), INTERMEDIATE_EVENT, convergingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(camundaStart2), INTERMEDIATE_EVENT, convergingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(convergingId, EXCLUSIVE_GATEWAY, idOf(camundaEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(6);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    // We cannot assert on the connecting gateway as we do not know what start event will be its source
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(camundaStart1), idOf(camundaStart2)),
      Arrays.asList(idOf(camundaEnd)),
      getGatewayWithId(gatewaysInModel, convergingId)
    );
  }

  @Test
  public void createFromExternalAndCamundaSources_singleExternalStartEndEvents() {
    final String traceId = "tracingId";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceId, now.minusSeconds(30)),
      createCloudEventOfType(EVENT_B, traceId, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_C, traceId, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_D, traceId, now)
    ));

    BpmnModelInstance modelInstance = singleStartSingleEndModel();
    final CamundaEventSourceEntryDto camundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto camundaStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      camundaSource
    );
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
      Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D, camundaStart, camundaEnd)
    );
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(EVENT_C), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, idOf(camundaStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaStart), INTERMEDIATE_EVENT, idOf(camundaEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(5);
  }

  @Test
  public void createFromExternalAndCamundaSources_multipleExternalStartEndEvents() {
    final Instant now = Instant.now();
    final String firstTrace = "firstTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTrace, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_B, firstTrace, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_C, firstTrace, now)
    ));
    final String secondTraceId = "secondTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_B, secondTraceId, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_D, secondTraceId, now)
    ));

    BpmnModelInstance modelInstance = singleStartSingleEndModel();
    final CamundaEventSourceEntryDto camundaSource = deployDefinitionWithInstanceAndCreateEventSource(
      modelInstance,
      EventScopeType.START_END
    );
    final EventTypeDto camundaStart = createCamundaEventTypeDto(PROCESS_ID_1, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_1, END_EVENT_ID_1, END_EVENT_ID_1);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      camundaSource
    );
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
      Arrays.asList(EVENT_A, EVENT_B, EVENT_C, EVENT_D, camundaStart, camundaEnd)
    );
    // The extra flow nodes are the connecting gateway and the one to split the two end events from the external
    // source model
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 2);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String connectingId = generateConnectionGatewayIdForDefinitionKey(Converging, EXTERNAL_EVENTS_INDEX_SUFFIX);
    final String externalId = generateGatewayIdForNode(EVENT_B, Diverging);
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, externalId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(externalId, EXCLUSIVE_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(externalId, EXCLUSIVE_GATEWAY, idOf(EVENT_D), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, connectingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, connectingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(connectingId, EXCLUSIVE_GATEWAY, idOf(camundaStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaStart), INTERMEDIATE_EVENT, idOf(camundaEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(8);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(2);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B)),
      Arrays.asList(idOf(EVENT_C), idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, externalId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_C), idOf(EVENT_D)),
      Arrays.asList(idOf(camundaStart)),
      getGatewayWithId(gatewaysInModel, connectingId)
    );
  }

  @Test
  public void createFromThreeSourcesWithOrder_external_processStartEnd_startEndEvents() {
    final String traceId = "tracingId";
    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceId, now.minusSeconds(30)),
      createCloudEventOfType(EVENT_B, traceId, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_C, traceId, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_D, traceId, now)
    ));

    final CamundaEventSourceEntryDto camundaProcessSource = deployDefinitionWithInstanceAndCreateEventSource(
      singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1),
      EventScopeType.PROCESS_INSTANCE,
      traceId
    );
    final EventTypeDto processStart = createCamundaProcessStartEventTypeDto(PROCESS_ID_1);
    final EventTypeDto processEnd = createCamundaProcessEndEventTypeDto(PROCESS_ID_1);
    final String processNodeId = generateTaskIdForDefinitionKey(
      camundaProcessSource.getConfiguration().getProcessDefinitionKey());

    final CamundaEventSourceEntryDto startEndSource = deployDefinitionWithInstanceAndCreateEventSource(
      singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2),
      EventScopeType.START_END,
      traceId
    );
    final EventTypeDto camundaStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      camundaProcessSource,
      startEndSource
    );
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertThat(mappings)
      .hasSize(7)
      .containsExactlyInAnyOrderEntriesOf(
        ImmutableMap.<String, EventMappingDto>builder()
          .put(generateNodeId(EVENT_A), getMappingForStart(EVENT_A))
          .put(generateNodeId(EVENT_B), getMappingForStart(EVENT_B))
          .put(generateNodeId(EVENT_C), getMappingForStart(EVENT_C))
          .put(generateNodeId(EVENT_D), getMappingForStart(EVENT_D))
          .put(processNodeId, getMappingForStartEnd(processStart, processEnd))
          .put(generateNodeId(camundaStart), getMappingForStart(camundaStart))
          .put(generateNodeId(camundaEnd), getMappingForStart(camundaEnd))
          .build()
      );

    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(EVENT_A), START_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(EVENT_C), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, processNodeId, CALL_ACTIVITY, generatedInstance);
    assertNodeConnection(processNodeId, CALL_ACTIVITY, idOf(camundaStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaStart), INTERMEDIATE_EVENT, idOf(camundaEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(6);
  }

  @Test
  public void createFromThreeSourcesWithOrder_processStartEnd_startEndEvents_external() {
    final String traceId = "tracingId";
    final CamundaEventSourceEntryDto camundaProcessSource = deployDefinitionWithInstanceAndCreateEventSource(
      singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1),
      EventScopeType.PROCESS_INSTANCE,
      traceId
    );

    final EventTypeDto processStart = createCamundaProcessStartEventTypeDto(PROCESS_ID_1);
    final EventTypeDto processEnd = createCamundaProcessEndEventTypeDto(PROCESS_ID_1);

    final CamundaEventSourceEntryDto startEndSource = deployDefinitionWithInstanceAndCreateEventSource(
      singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2),
      EventScopeType.START_END,
      traceId
    );
    final EventTypeDto camundaStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final Instant now = Instant.now();
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceId, now),
      createCloudEventOfType(EVENT_B, traceId, now.plusSeconds(10)),
      createCloudEventOfType(EVENT_C, traceId, now.plusSeconds(20)),
      createCloudEventOfType(EVENT_D, traceId, now.plusSeconds(30))
    ));

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      camundaProcessSource,
      startEndSource,
      createExternalEventAllGroupsSourceEntry()
    );
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
      Arrays.asList(processStart, processEnd, camundaStart, camundaEnd, EVENT_A, EVENT_B, EVENT_C, EVENT_D)
    );

    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(processStart), START_EVENT, idOf(processEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(
      idOf(processEnd),
      INTERMEDIATE_EVENT,
      idOf(camundaStart),
      INTERMEDIATE_EVENT,
      generatedInstance
    );
    assertNodeConnection(
      idOf(camundaStart),
      INTERMEDIATE_EVENT,
      idOf(camundaEnd),
      INTERMEDIATE_EVENT,
      generatedInstance
    );
    assertNodeConnection(idOf(camundaEnd), INTERMEDIATE_EVENT, idOf(EVENT_A), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_A), INTERMEDIATE_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(EVENT_C), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_D), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(7);
  }

  @Test
  public void createFromThreeSourcesWithOrder_startEndEvents_external_processStartEnd() throws SQLException {
    final String traceId = "tracingId";
    final Instant now = Instant.now();

    final ProcessInstanceEngineDto startEndInstance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        singleStartSingleEndModel(PROCESS_ID_2, START_EVENT_ID_2, END_EVENT_ID_2),
        Collections.emptyMap(),
        traceId,
        null
      );
    engineDatabaseExtension.changeProcessInstanceStartDate(
      startEndInstance.getId(),
      OffsetDateTime.ofInstant(now.minusSeconds(50), ZoneId.systemDefault())
    );
    importEngineEntities();
    final CamundaEventSourceEntryDto startEndSource = createCamundaSourceEntry(
      startEndInstance.getProcessDefinitionKey(),
      EventScopeType.START_END
    );

    final EventTypeDto camundaStart = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto camundaEnd = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, traceId, now.minusSeconds(30)),
      createCloudEventOfType(EVENT_B, traceId, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_C, traceId, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_D, traceId, now)
    ));

    final CamundaEventSourceEntryDto camundaProcessSource = deployDefinitionWithInstanceAndCreateEventSource(
      singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1),
      EventScopeType.PROCESS_INSTANCE,
      traceId
    );

    final EventTypeDto processStart = createCamundaProcessStartEventTypeDto(PROCESS_ID_1);
    final EventTypeDto processEnd = createCamundaProcessEndEventTypeDto(PROCESS_ID_1);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      startEndSource,
      createExternalEventAllGroupsSourceEntry(),
      camundaProcessSource
    );
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
      Arrays.asList(processStart, processEnd, camundaStart, camundaEnd, EVENT_A, EVENT_B, EVENT_C, EVENT_D)
    );

    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size());

    // then the model elements are of the correct type and connected to expected nodes correctly
    assertNodeConnection(idOf(camundaStart), START_EVENT, idOf(camundaEnd), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd), INTERMEDIATE_EVENT, idOf(EVENT_A), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_A), INTERMEDIATE_EVENT, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, idOf(EVENT_C), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, idOf(EVENT_D), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, idOf(processStart), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(processStart), INTERMEDIATE_EVENT, idOf(processEnd), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(processEnd), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(7);
  }

  @Test
  public void createFromThreeSourcesWithOrder_complexModelConfiguration() {
    final Instant now = Instant.now();
    final String firstTraceId = "firstTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, firstTraceId, now.minusSeconds(30)),
      createCloudEventOfType(EVENT_B, firstTraceId, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_C, firstTraceId, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_D, firstTraceId, now)
    ));
    final String secondTraceId = "secondTracingId";
    ingestEventAndProcessTraces(Arrays.asList(
      createCloudEventOfType(EVENT_A, secondTraceId, now.minusSeconds(30)),
      createCloudEventOfType(EVENT_C, secondTraceId, now.minusSeconds(20)),
      createCloudEventOfType(EVENT_B, secondTraceId, now.minusSeconds(10)),
      createCloudEventOfType(EVENT_D, secondTraceId, now)
    ));

    final CamundaEventSourceEntryDto camundaProcessSource = deployDefinitionWithInstanceAndCreateEventSource(
      singleStartSingleEndModel(PROCESS_ID_1, START_EVENT_ID_1, END_EVENT_ID_1),
      EventScopeType.PROCESS_INSTANCE
    );
    final EventTypeDto processStart = createCamundaProcessStartEventTypeDto(PROCESS_ID_1);
    final EventTypeDto processEnd = createCamundaProcessEndEventTypeDto(PROCESS_ID_1);
    final String processNodeId = generateTaskIdForDefinitionKey(
      camundaProcessSource.getConfiguration().getProcessDefinitionKey());

    final CamundaEventSourceEntryDto startEndSource = deployDefinitionWithInstanceAndCreateEventSource(
      multipleStartMultipleEndModel(PROCESS_ID_2),
      EventScopeType.START_END
    );
    processEventTracesAndSequences();
    final EventTypeDto camundaStart1 = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_1, START_EVENT_ID_1);
    final EventTypeDto camundaStart2 = createCamundaEventTypeDto(PROCESS_ID_2, START_EVENT_ID_2, START_EVENT_ID_2);
    final EventTypeDto camundaEnd1 = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_1, END_EVENT_ID_1);
    final EventTypeDto camundaEnd2 = createCamundaEventTypeDto(PROCESS_ID_2, END_EVENT_ID_2, END_EVENT_ID_2);

    final List<EventSourceEntryDto<?>> sources = Arrays.asList(
      createExternalEventAllGroupsSourceEntry(),
      camundaProcessSource,
      startEndSource
    );
    final EventProcessMappingCreateRequestDto createRequestDto = buildAutogenerateCreateRequestDto(sources);

    // when
    final EventProcessMappingResponseDto processMapping =
      autogenerateProcessAndGetMappingResponse(createRequestDto);

    // then
    final Map<String, EventMappingDto> mappings = processMapping.getMappings();
    final BpmnModelInstance generatedInstance = BpmnModelUtil.parseBpmnModel(processMapping.getXml());
    assertProcessMappingConfiguration(processMapping, sources, EventProcessState.MAPPED);

    // then the mappings contain the correct events and are all in the model
    assertThat(mappings)
      .hasSize(9)
      .containsExactlyInAnyOrderEntriesOf(
        ImmutableMap.<String, EventMappingDto>builder()
          .put(generateNodeId(EVENT_A), getMappingForStart(EVENT_A))
          .put(generateNodeId(EVENT_B), getMappingForStart(EVENT_B))
          .put(generateNodeId(EVENT_C), getMappingForStart(EVENT_C))
          .put(generateNodeId(EVENT_D), getMappingForStart(EVENT_D))
          .put(processNodeId, getMappingForStartEnd(processStart, processEnd))
          .put(generateNodeId(camundaStart1), getMappingForStart(camundaStart1))
          .put(generateNodeId(camundaStart2), getMappingForStart(camundaStart2))
          .put(generateNodeId(camundaEnd1), getMappingForStart(camundaEnd1))
          .put(generateNodeId(camundaEnd2), getMappingForStart(camundaEnd2))
          .build()
      );

    // The additional flow nodes are the gateways that have been added
    assertThat(generatedInstance.getModelElementsByType(FlowNode.class)).hasSize(mappings.size() + 5);

    // then the model elements are of the correct type and connected to expected nodes correctly
    final String divergingId1 = generateGatewayIdForNode(EVENT_A, Diverging);
    final String convergingId1 = generateGatewayIdForNode(EVENT_D, Converging);
    final String convergingId2 = generateModelGatewayIdForSource(startEndSource, Converging);
    final String divergingId2 = generateModelGatewayIdForSource(startEndSource, Diverging);
    // The next source is responsible for creating the diverging connecting gateway so the ID uses its definition key
    final String connectingId = generateConnectionGatewayIdForDefinitionKey(
      Diverging,
      startEndSource.getConfiguration().getProcessDefinitionKey()
    );


    assertNodeConnection(idOf(EVENT_A), START_EVENT, divergingId1, PARALLEL_GATEWAY, generatedInstance);
    assertNodeConnection(divergingId1, PARALLEL_GATEWAY, idOf(EVENT_B), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(divergingId1, PARALLEL_GATEWAY, idOf(EVENT_C), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_B), INTERMEDIATE_EVENT, convergingId1, PARALLEL_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(EVENT_C), INTERMEDIATE_EVENT, convergingId1, PARALLEL_GATEWAY, generatedInstance);
    assertNodeConnection(convergingId1, PARALLEL_GATEWAY, idOf(EVENT_D), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(EVENT_D), INTERMEDIATE_EVENT, processNodeId, CALL_ACTIVITY, generatedInstance);
    assertNodeConnection(processNodeId, CALL_ACTIVITY, connectingId, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(connectingId, EXCLUSIVE_GATEWAY, idOf(camundaStart1), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(connectingId, EXCLUSIVE_GATEWAY, idOf(camundaStart2), INTERMEDIATE_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaStart1), INTERMEDIATE_EVENT, convergingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(idOf(camundaStart2), INTERMEDIATE_EVENT, convergingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(convergingId2, EXCLUSIVE_GATEWAY, divergingId2, EXCLUSIVE_GATEWAY, generatedInstance);
    assertNodeConnection(divergingId2, EXCLUSIVE_GATEWAY, idOf(camundaEnd1), END_EVENT, generatedInstance);
    assertNodeConnection(divergingId2, EXCLUSIVE_GATEWAY, idOf(camundaEnd2), END_EVENT, generatedInstance);
    assertNodeConnection(idOf(camundaEnd1), END_EVENT, null, null, generatedInstance);
    assertNodeConnection(idOf(camundaEnd2), END_EVENT, null, null, generatedInstance);

    // and the expected number of sequence flows exist
    assertThat(generatedInstance.getModelElementsByType(SequenceFlow.class)).hasSize(15);

    // and the gateways have the expected source and target events
    final Collection<Gateway> gatewaysInModel = generatedInstance.getModelElementsByType(Gateway.class);
    assertThat(gatewaysInModel).hasSize(5);
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_A)),
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      getGatewayWithId(gatewaysInModel, divergingId1)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(EVENT_B), idOf(EVENT_C)),
      Arrays.asList(idOf(EVENT_D)),
      getGatewayWithId(gatewaysInModel, convergingId1)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(processNodeId),
      Arrays.asList(idOf(camundaStart1), idOf(camundaStart2)),
      getGatewayWithId(gatewaysInModel, connectingId)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(idOf(camundaStart1), idOf(camundaStart2)),
      Arrays.asList(divergingId2),
      getGatewayWithId(gatewaysInModel, convergingId2)
    );
    assertGatewayWithSourcesAndTargets(
      Arrays.asList(convergingId2),
      Arrays.asList(idOf(camundaEnd1), idOf(camundaEnd2)),
      getGatewayWithId(gatewaysInModel, divergingId2)
    );

    // and that the mapped events all exist in the returned event counts list
    final List<EventTypeDto> eventCounts = getEventCountsAsEventTypeDtos(sources);
    assertThat(eventCounts).containsAll(getMappedEventTypeDtosFromMappings(mappings));
  }

  private EventMappingDto getMappingForStartEnd(final EventTypeDto processStart,
                                                final EventTypeDto processEnd) {
    return EventMappingDto.builder().start(processStart).end(processEnd).build();
  }

  private EventMappingDto getMappingForStart(final EventTypeDto processStart) {
    return EventMappingDto.builder().start(processStart).build();
  }

}
