/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.camunda.optimize.service.util.importing.ZeebeConstants;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.ZeebeBpmnModels.END_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createLoopingProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleUserTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class ZeebeProcessInstanceImportIT extends AbstractZeebeIT {

  private Supplier<OptimizeIntegrationTestException> eventNotFoundExceptionSupplier =
    () -> new OptimizeIntegrationTestException("Cannot find exported event");

  @Test
  public void importCompletedZeebeProcessInstanceDataInOneBatch_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(processName));
    final ProcessInstanceEvent deployedInstance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    waitUntilProcessInstanceEventsExported();
    importAllZeebeEntities();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(String.valueOf(deployedInstance.getBpmnProcessId()));
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX);
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate()).isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getEndDate()).isEqualTo(getExpectedEndDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getDuration()).isEqualTo(getExpectedDurationForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .containsExactlyInAnyOrder(
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(START_EVENT).get(0).getKey()))
              .flowNodeId(START_EVENT)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.START_EVENT))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(START_EVENT)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(START_EVENT)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(START_EVENT)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build(),
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(END_EVENT).get(0).getKey()))
              .flowNodeId(END_EVENT)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.END_EVENT))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(END_EVENT)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(END_EVENT)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(END_EVENT)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build()
          );
      });
  }

  @Test
  public void importCompletedZeebeProcessInstanceDataInMultipleBatches_allDataSavedToOptimizeProcessInstance() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess("someProcess"));
    zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    waitUntilProcessInstanceEventsExported();
    importAllZeebeEntities();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
        assertThat(savedInstance.getFlowNodeInstances()).isEmpty();
      });

    // when
    importAllZeebeEntities();

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
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(10);
    importAllZeebeEntities();

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
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleUserTaskProcess(processName));
    final ProcessInstanceEvent deployedInstance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    waitUntilProcessInstanceEventsExported();
    importAllZeebeEntities();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(String.valueOf(deployedInstance.getBpmnProcessId()));
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX);
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate()).isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getEndDate()).isNull();
        assertThat(savedInstance.getDuration()).isNull();
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .containsExactlyInAnyOrder(
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(START_EVENT).get(0).getKey()))
              .flowNodeId(START_EVENT)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.START_EVENT))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(START_EVENT)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(START_EVENT)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(START_EVENT)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build(),
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(USER_TASK).get(0).getKey()))
              .flowNodeId(USER_TASK)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.USER_TASK))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(USER_TASK)))
              .endDate(null)
              .totalDurationInMs(null)
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build()
          );
      });
  }

  @Test
  public void importCanceledZeebeProcessInstanceData_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(processName));
    final ProcessInstanceEvent deployedInstance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
    // We wait for the service task to be exported before cancelling the process
    // (1 * process event, 2 * "start_event" events). Then again for the import of cancellation events (2 cancel events)
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.cancelProcessInstance(deployedInstance.getProcessInstanceKey());
    waitUntilMinimumProcessInstanceEventsExportedCount(6);

    // when
    importAllZeebeEntities();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(String.valueOf(deployedInstance.getBpmnProcessId()));
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX);
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate()).isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getEndDate()).isEqualTo(getExpectedEndDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getDuration()).isEqualTo(getExpectedDurationForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(2)
          .containsExactlyInAnyOrder(
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(START_EVENT).get(0).getKey()))
              .flowNodeId(START_EVENT)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.START_EVENT))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(START_EVENT)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(START_EVENT)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(START_EVENT)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build(),
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(SERVICE_TASK).get(0).getKey()))
              .flowNodeId(SERVICE_TASK)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.SERVICE_TASK))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(SERVICE_TASK)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(SERVICE_TASK)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(SERVICE_TASK)))
              .canceled(true)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build()
          );
      });
  }

  @Test
  public void importZeebeProcessInstanceDataFromMultipleDays_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(processName));
    final ProcessInstanceEvent deployedInstance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.getZeebeClock().setCurrentTime(Instant.now().plus(1, ChronoUnit.DAYS));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK);
    waitUntilMinimumProcessInstanceEventsExportedCount(5);
    importAllZeebeEntities();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
      getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(String.valueOf(deployedInstance.getBpmnProcessId()));
        assertThat(savedInstance.getProcessDefinitionVersion()).isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX);
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getIncidents()).isEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate()).isEqualTo(getExpectedStartDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getEndDate()).isEqualTo(getExpectedEndDateForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getDuration()).isEqualTo(getExpectedDurationForEvents(exportedEvents.get(processName)));
        assertThat(savedInstance.getFlowNodeInstances())
          .hasSize(3)
          .containsExactlyInAnyOrder(
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(START_EVENT).get(0).getKey()))
              .flowNodeId(START_EVENT)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.START_EVENT))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(START_EVENT)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(START_EVENT)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(START_EVENT)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build(),
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(SERVICE_TASK).get(0).getKey()))
              .flowNodeId(SERVICE_TASK)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.SERVICE_TASK))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(SERVICE_TASK)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(SERVICE_TASK)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(SERVICE_TASK)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build(),
            FlowNodeInstanceDto.builder()
              .flowNodeInstanceId(String.valueOf(exportedEvents.get(END_EVENT).get(0).getKey()))
              .flowNodeId(END_EVENT)
              .flowNodeType(BpmnModelUtil.getFlowNodeTypeForBpmnElementType(BpmnElementType.END_EVENT))
              .processInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
              .startDate(getExpectedStartDateForEvents(exportedEvents.get(END_EVENT)))
              .endDate(getExpectedEndDateForEvents(exportedEvents.get(END_EVENT)))
              .totalDurationInMs(getExpectedDurationForEvents(exportedEvents.get(END_EVENT)))
              .canceled(false)
              // we always expect these fields to be null or empty for zeebe flow node instances
              .userTaskInstanceId(null)
              .idleDurationInMs(null)
              .workDurationInMs(null)
              .dueDate(null)
              .deleteReason(null)
              .assigneeOperations(Collections.emptyList())
              .candidateGroupOperations(Collections.emptyList())
              .build()
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
    waitUntilProcessInstanceEventsExported();
    importAllZeebeEntities();

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
    waitUntilProcessInstanceEventsExported();
    importAllZeebeEntities();

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
    final Process deployedProcessV1 = zeebeExtension.deployProcess(createStartEndProcess(processName));
    final ProcessInstanceEvent v1Instance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcessV1.getBpmnProcessId());
    final Process deployedProcessV2 = zeebeExtension.deployProcess(createStartEndProcess(processName));
    final ProcessInstanceEvent v2Instance =
      zeebeExtension.startProcessInstanceForProcess(deployedProcessV2.getBpmnProcessId());

    // when
    waitUntilProcessInstanceEventsExported();
    importAllZeebeEntities();

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
    final Process deployedProcess = zeebeExtension.deployProcess(createLoopingProcess(processName));
    zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    waitUntilProcessInstanceEventsExported();
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK, Map.of("loop", true));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK, Map.of("loop", false));
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    importAllZeebeEntities();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getFlowNodeInstances())
        .filteredOn(flowNodeInstance -> flowNodeInstance.getFlowNodeId().equals(SERVICE_TASK))
        .hasSizeGreaterThan(1));
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

  @SneakyThrows
  private void waitUntilProcessInstanceEventsExported() {
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
  }

  @SneakyThrows
  private void waitUntilMinimumProcessInstanceEventsExportedCount(final int minExportedEventCount) {
    final String expectedIndex =
      zeebeExtension.getZeebeRecordPrefix() + "-" + ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
    final OptimizeElasticsearchClient esClient =
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    Awaitility.dontCatchUncaughtExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .indices()
          .exists(new GetIndexRequest(expectedIndex), esClient.requestOptions())
      ).isTrue());
    final CountRequest definitionCountRequest =
      new CountRequest(expectedIndex)
        .query(getQueryForProcessableEvents());
    Awaitility.catchUncaughtExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .count(definitionCountRequest, esClient.requestOptions())
          .getCount())
        .isGreaterThanOrEqualTo(minExportedEventCount));
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

  private BoolQueryBuilder getQueryForProcessableEvents() {
    return boolQuery().must(termsQuery(
      ZeebeProcessInstanceRecordDto.Fields.intent,
      ProcessInstanceIntent.ELEMENT_ACTIVATING,
      ProcessInstanceIntent.ELEMENT_COMPLETED,
      ProcessInstanceIntent.ELEMENT_TERMINATED
    ));
  }

}
