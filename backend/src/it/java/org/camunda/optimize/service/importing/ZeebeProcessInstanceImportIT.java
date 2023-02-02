/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.ZeebeBpmnModels.END_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.END_EVENT_2;
import static org.camunda.optimize.util.ZeebeBpmnModels.SEND_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createInclusiveGatewayProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createLoopingProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSendTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleUserTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSingleStartDoubleEndEventProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createTerminateEndEventProcess;

public class ZeebeProcessInstanceImportIT extends AbstractZeebeIT {

  private final Supplier<OptimizeIntegrationTestException> eventNotFoundExceptionSupplier =
    () -> new OptimizeIntegrationTestException("Cannot find exported event");

  @Test
  public void importCompletedZeebeProcessInstanceDataInOneBatch_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance = deployAndStartInstanceForProcess(createStartEndProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate())
          .isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getEndDate())
          .isEqualTo(getExpectedEndDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getDuration())
          .isEqualTo(getExpectedDurationForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .containsExactlyInAnyOrder(
            createFlowNodeInstance(deployedInstance, exportedEvents, START_EVENT, BpmnElementType.START_EVENT),
            createFlowNodeInstance(deployedInstance, exportedEvents, END_EVENT, BpmnElementType.END_EVENT)
          );
      });
  }

  @Test
  public void importCompletedZeebeProcessInstanceDataInMultipleBatches_allDataSavedToOptimizeProcessInstance() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then process activating event has been imported
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
        assertThat(savedInstance.getFlowNodeInstances()).isEmpty();
      });

    // when
    importAllZeebeEntitiesFromLastIndex(); // fetch process activated event - not imported
    importAllZeebeEntitiesFromLastIndex(); // fetch and import flownode activating event

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
        assertThat(savedInstance.getFlowNodeInstances()).hasSize(1)
          .allSatisfy(flowNodeInstance -> assertThat(flowNodeInstance.getTotalDurationInMs()).isNull())
          .extracting(FlowNodeInstanceDto::getFlowNodeId)
          .containsExactly(START_EVENT);
      });

    // when we increase the page size
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(15);
    importAllZeebeEntitiesFromScratch();

    // then we get the rest of the process data
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
        assertThat(savedInstance.getFlowNodeInstances()).hasSize(2)
          .allSatisfy(flowNodeInstance -> assertThat(flowNodeInstance.getTotalDurationInMs()).isNotNull())
          .extracting(FlowNodeInstanceDto::getFlowNodeId)
          .containsExactlyInAnyOrder(START_EVENT, END_EVENT);
      });
  }

  @Test
  public void importRunningZeebeProcessInstanceData_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleUserTaskProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate())
          .isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getEndDate()).isNull();
        assertThat(savedInstance.getDuration()).isNull();
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .containsExactlyInAnyOrder(
            createFlowNodeInstance(deployedInstance, exportedEvents, START_EVENT, BpmnElementType.START_EVENT),
            new FlowNodeInstanceDto(
              String.valueOf(deployedInstance.getBpmnProcessId()),
              String.valueOf(deployedInstance.getVersion()),
              null,
              String.valueOf(deployedInstance.getProcessInstanceKey()),
              USER_TASK,
              getBpmnElementTypeNameForType(BpmnElementType.USER_TASK),
              String.valueOf(exportedEvents.get(USER_TASK).get(0).getKey())
            )
              .setStartDate(getExpectedStartDateForEvents(exportedEvents.get(USER_TASK)))
              .setCanceled(false)
          );
      });
  }

  @Test
  public void importCanceledZeebeProcessInstanceData_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleServiceTaskProcess(processName));

    // We wait for the service task to be exported before cancelling the process
    // (1 * process event, 2 * "start_event" events). Then again for the import of cancellation events (2 cancel events)
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.cancelProcessInstance(deployedInstance.getProcessInstanceKey());
    waitUntilMinimumProcessInstanceEventsExportedCount(6);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate())
          .isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getEndDate())
          .isEqualTo(getExpectedEndDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getDuration()).isEqualTo(getExpectedDurationForEvents(exportedEvents.get(
          deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .containsExactlyInAnyOrder(
            createFlowNodeInstance(deployedInstance, exportedEvents, START_EVENT, BpmnElementType.START_EVENT),
            createFlowNodeInstance(deployedInstance, exportedEvents, SERVICE_TASK, BpmnElementType.SERVICE_TASK)
              .setCanceled(true)
          );
      });
  }

  @Test
  @SneakyThrows
  public void importZeebeProcessInstanceDataFromMultipleDays_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleServiceTaskProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.setClock(Instant.now().plus(1, ChronoUnit.DAYS));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK);
    waitUntilMinimumProcessInstanceEventsExportedCount(5);
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate())
          .isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getEndDate())
          .isEqualTo(getExpectedEndDateForEvents(exportedEvents.get(deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getDuration()).isEqualTo(getExpectedDurationForEvents(exportedEvents.get(
          deployedInstance.getBpmnProcessId())));
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(3)
          .containsExactlyInAnyOrder(
            createFlowNodeInstance(deployedInstance, exportedEvents, START_EVENT, BpmnElementType.START_EVENT),
            createFlowNodeInstance(deployedInstance, exportedEvents, SERVICE_TASK, BpmnElementType.SERVICE_TASK),
            createFlowNodeInstance(deployedInstance, exportedEvents, END_EVENT, BpmnElementType.END_EVENT)
          );
      });
  }

  @Test
  public void importZeebeProcessInstanceData_multipleInstancesForSameProcess() {
    // given
    final String processName = "someProcess";
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(processName));
    final ProcessInstanceEvent firstInstance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
    final ProcessInstanceEvent secondInstance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    // Each instance generates 6 events
    waitUntilMinimumProcessInstanceEventsExportedCount(12);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .hasSize(2)
      .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances()).hasSize(2))
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        String.valueOf(firstInstance.getProcessInstanceKey()),
        String.valueOf(secondInstance.getProcessInstanceKey())
      );
  }

  @Test
  public void importZeebeProcessInstanceData_instancesForDifferentProcesses() {
    // given
    final ProcessInstanceEvent firstInstance =
      zeebeExtension.startProcessInstanceForProcess(
        zeebeExtension.deployProcess(createStartEndProcess("firstProcess")).getBpmnProcessId());
    final ProcessInstanceEvent secondInstance =
      zeebeExtension.startProcessInstanceForProcess(
        zeebeExtension.deployProcess(createStartEndProcess("secondProcess")).getBpmnProcessId());

    // when
    // The first instance generates 6 events, so the 7th indicates that both processes have been exported
    waitUntilMinimumProcessInstanceEventsExportedCount(7);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .hasSize(2)
      .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances()).hasSize(2))
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        String.valueOf(firstInstance.getProcessInstanceKey()),
        String.valueOf(secondInstance.getProcessInstanceKey())
      );
  }

  @Test
  public void importZeebeProcessInstanceData_instancesWithDifferentVersionsOfSameProcess() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent v1Instance =
      deployAndStartInstanceForProcess(createStartEndProcess(processName, processName));
    final ProcessInstanceEvent v2Instance =
      deployAndStartInstanceForProcess(createStartEndProcess(processName, processName));

    // when
    // The first instance generates 6 events, so the 7th indicates that both processes have been exported
    waitUntilMinimumProcessInstanceEventsExportedCount(12);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .hasSize(2)
      .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances()).hasSize(2))
      .extracting(ProcessInstanceDto::getProcessInstanceId, ProcessInstanceDto::getProcessDefinitionVersion)
      .containsExactlyInAnyOrder(
        Tuple.tuple(String.valueOf(v1Instance.getProcessInstanceKey()), "1"),
        Tuple.tuple(String.valueOf(v2Instance.getProcessInstanceKey()), "2")
      );
  }

  @Test
  public void importZeebeProcessInstanceData_processContainsLoop() {
    // given
    final String processName = "someProcess";
    deployAndStartInstanceForProcess(createLoopingProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK, Map.of("loop", true));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK, Map.of("loop", false));
    waitUntilMinimumProcessInstanceEventsExportedCount(18);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getFlowNodeInstances())
        .filteredOn(flowNodeInstance -> flowNodeInstance.getFlowNodeId().equals(SERVICE_TASK))
        .hasSizeGreaterThan(1));
  }

  @DisabledIfSystemProperty(named = "zeebe.docker.version", matches = "8.0.0")
  @Test
  public void importZeebeProcessInstanceData_processStartedDuringProcess() {
    // given
    final String processName = "someProcess";
    final Process process = zeebeExtension.deployProcess(createSingleStartDoubleEndEventProcess(processName));
    zeebeExtension.startProcessInstanceBeforeElementWithIds(process.getBpmnProcessId(), END_EVENT, END_EVENT_2);

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getEndDate()).isNotNull();
        assertThat(instance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
        assertThat(instance.getFlowNodeInstances())
          .extracting(FlowNodeInstanceDto::getFlowNodeType)
          .containsExactlyInAnyOrder(
            BpmnElementType.END_EVENT.getElementTypeName().get(),
            BpmnElementType.END_EVENT.getElementTypeName().get()
          );
      });
  }

  @DisabledIfSystemProperty(named = "zeebe.docker.version", matches = "8.0.0")
  @Test
  public void importZeebeProcessInstanceData_processContainsTerminateEndEvent() {
    // given
    final String processName = "someProcess";
    deployAndStartInstanceForProcess(createTerminateEndEventProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(2);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getFlowNodeInstances())
        .extracting(FlowNodeInstanceDto::getFlowNodeType)
        .containsExactlyInAnyOrder(
          BpmnElementType.START_EVENT.getElementTypeName().get(),
          BpmnElementType.END_EVENT.getElementTypeName().get()
        ));
  }

  @DisabledIfSystemProperty(named = "zeebe.docker.version", matches = "8.0.0")
  @Test
  public void importZeebeProcessInstanceData_processContainsInclusiveGateway() {
    // given
    final String processName = "someProcess";
    final Process process = zeebeExtension.deployProcess(createInclusiveGatewayProcess(processName));
    zeebeExtension.startProcessInstanceWithVariables(process.getBpmnProcessId(), Map.of("varName", "a,b"));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getFlowNodeInstances())
        .extracting(FlowNodeInstanceDto::getFlowNodeType)
        .containsExactlyInAnyOrder(
          BpmnElementType.START_EVENT.getElementTypeName().get(),
          BpmnElementType.INCLUSIVE_GATEWAY.getElementTypeName().get(),
          BpmnElementType.END_EVENT.getElementTypeName().get(),
          BpmnElementType.END_EVENT.getElementTypeName().get()
        ));
  }

  @Test
  public void importSendTaskZeebeProcessInstanceData_flowNodeInstancesCreatedCorrectly() {
    // given
    final ProcessInstanceEvent processInstance = deployAndStartInstanceForProcess(createSendTaskProcess("someProcess"));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .allSatisfy(flowNodeInstanceDto -> {
            assertThat(flowNodeInstanceDto)
              .hasFieldOrPropertyWithValue(FlowNodeInstanceDto.Fields.definitionKey, processInstance.getBpmnProcessId())
              .hasFieldOrPropertyWithValue(
                FlowNodeInstanceDto.Fields.definitionVersion,
                String.valueOf(processInstance.getVersion())
              )
              .hasFieldOrPropertyWithValue(FlowNodeInstanceDto.Fields.tenantId, null);
          })
          .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getFlowNodeType)
          .containsExactlyInAnyOrder(
            Tuple.tuple(START_EVENT, getBpmnElementTypeNameForType(BpmnElementType.START_EVENT)),
            Tuple.tuple(SEND_TASK, getBpmnElementTypeNameForType(BpmnElementType.SEND_TASK))
          );
      });
  }

  private FlowNodeInstanceDto createFlowNodeInstance(final ProcessInstanceEvent deployedInstance,
                                                     final Map<String, List<ZeebeProcessInstanceRecordDto>> events,
                                                     final String eventId,
                                                     final BpmnElementType eventType) {
    return new FlowNodeInstanceDto(
      String.valueOf(deployedInstance.getBpmnProcessId()),
      String.valueOf(deployedInstance.getVersion()),
      null,
      String.valueOf(deployedInstance.getProcessInstanceKey()),
      eventId,
      getBpmnElementTypeNameForType(eventType),
      String.valueOf(events.get(eventId).get(0).getKey())
    )
      .setStartDate(getExpectedStartDateForEvents(events.get(eventId)))
      .setEndDate(getExpectedEndDateForEvents(events.get(eventId)))
      .setTotalDurationInMs(getExpectedDurationForEvents(events.get(eventId)))
      .setCanceled(false);
  }

  @SneakyThrows
  private Map<String, List<ZeebeProcessInstanceRecordDto>> getZeebeExportedProcessInstanceEventsByElementId() {
    final String expectedIndex =
      zeebeExtension.getZeebeRecordPrefix() + "-" + ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
    final OptimizeElasticsearchClient esClient =
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    SearchRequest searchRequest = new SearchRequest()
      .indices(expectedIndex)
      .source(new SearchSourceBuilder()
                .query(getQueryForProcessableEvents())
                .trackTotalHits(true)
                .size(100));
    final SearchResponse searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    return ElasticsearchReaderUtil.mapHits(
      searchResponse.getHits(),
      ZeebeProcessInstanceRecordDto.class,
      embeddedOptimizeExtension.getObjectMapper()
    ).stream()
      .collect(Collectors.groupingBy(event -> event.getValue().getElementId()));
  }

  private long getExpectedDurationForEvents(final List<ZeebeProcessInstanceRecordDto> eventsForElement) {
    final ZeebeProcessInstanceRecordDto startOfElement = eventsForElement.stream()
      .filter(event -> event.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING))
      .findFirst().orElseThrow(eventNotFoundExceptionSupplier);
    final ZeebeProcessInstanceRecordDto endOfElement = eventsForElement.stream()
      .filter(event -> event
        .getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED) ||
        event.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED))
      .findFirst().orElseThrow(eventNotFoundExceptionSupplier);
    return endOfElement.getTimestamp() - startOfElement.getTimestamp();
  }

  private OffsetDateTime getExpectedStartDateForEvents(final List<ZeebeProcessInstanceRecordDto> eventsForElement) {
    final ZeebeProcessInstanceRecordDto startOfElement = eventsForElement.stream()
      .filter(event -> event.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING))
      .findFirst().orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(startOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  private OffsetDateTime getExpectedEndDateForEvents(final List<ZeebeProcessInstanceRecordDto> eventsForElement) {
    final ZeebeProcessInstanceRecordDto endOfElement = eventsForElement.stream()
      .filter(event -> event
        .getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED) ||
        event.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED))
      .findFirst().orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(endOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  private String getBpmnElementTypeNameForType(final BpmnElementType type) {
    return type.getElementTypeName()
      .orElseThrow(() -> new OptimizeRuntimeException("Cannot find name for type: " + type));
  }

}
