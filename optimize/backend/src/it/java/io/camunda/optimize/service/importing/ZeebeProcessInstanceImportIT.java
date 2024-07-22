/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static io.camunda.optimize.util.ZeebeBpmnModels.COMPENSATION_EVENT_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.END_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.END_EVENT_2;
import static io.camunda.optimize.util.ZeebeBpmnModels.SEND_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK_WITH_COMPENSATION_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_CATCH;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_GATEWAY_CATCH;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_INTERRUPTING_BOUNDARY;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_NON_INTERRUPTING_BOUNDARY;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_END;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_FIRST_SIGNAL;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_SECOND_SIGNAL;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_THIRD_SIGNAL;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_WAIT_FOR_FIRST_SIGNAL_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_WAIT_FOR_SECOND_SIGNAL_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_PROCESS_WAIT_FOR_THIRD_SIGNAL_GATEWAY;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_START_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_START_INT_SUB_PROCESS;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_START_NON_INT_SUB_PROCESS;
import static io.camunda.optimize.util.ZeebeBpmnModels.SIGNAL_THROW;
import static io.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createCompensationEventProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createInclusiveGatewayProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createLoopingProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createProcessWith83SignalEvents;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSendTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleUserTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSingleStartDoubleEndEventProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createTerminateEndEventProcess;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;

public class ZeebeProcessInstanceImportIT extends AbstractCCSMIT {

  private final Supplier<OptimizeIntegrationTestException> eventNotFoundExceptionSupplier =
      () -> new OptimizeIntegrationTestException("Cannot find exported event");

  @Test
  public void
      importCompletedZeebeProcessInstanceDataInOneBatch_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createStartEndProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
        getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(deployedInstance.getBpmnProcessId());
              assertThat(savedInstance.getProcessDefinitionVersion())
                  .isEqualTo(String.valueOf(deployedInstance.getVersion()));
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getState())
                  .isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getBusinessKey()).isNull();
              assertThat(savedInstance.getIncidents()).isEmpty();
              assertThat(savedInstance.getVariables()).isEmpty();
              assertThat(savedInstance.getStartDate())
                  .isEqualTo(
                      getExpectedStartDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getEndDate())
                  .isEqualTo(
                      getExpectedEndDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getDuration())
                  .isEqualTo(
                      getExpectedDurationForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getFlowNodeInstances())
                  .hasSize(2)
                  .containsExactlyInAnyOrder(
                      createFlowNodeInstance(
                          deployedInstance,
                          exportedEvents,
                          START_EVENT,
                          BpmnElementType.START_EVENT),
                      createFlowNodeInstance(
                          deployedInstance, exportedEvents, END_EVENT, BpmnElementType.END_EVENT));
            });
  }

  @Test
  public void
      importCompletedZeebeProcessInstanceDataInMultipleBatches_allDataSavedToOptimizeProcessInstance() {
    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then process activating event has been imported
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
              assertThat(savedInstance.getFlowNodeInstances()).isEmpty();
            });

    // when
    importAllZeebeEntitiesFromLastIndex(); // fetch process activated event - not imported
    importAllZeebeEntitiesFromLastIndex(); // fetch and import flownode activating event

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
              assertThat(savedInstance.getFlowNodeInstances())
                  .hasSize(1)
                  .allSatisfy(
                      flowNodeInstance ->
                          assertThat(flowNodeInstance.getTotalDurationInMs()).isNull())
                  .extracting(FlowNodeInstanceDto::getFlowNodeId)
                  .containsExactly(START_EVENT);
            });

    // when we increase the page size
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(15);
    embeddedOptimizeExtension.reloadConfiguration();
    importAllZeebeEntitiesFromScratch();

    // then we get the rest of the process data
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getState())
                  .isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
              assertThat(savedInstance.getFlowNodeInstances())
                  .hasSize(2)
                  .allSatisfy(
                      flowNodeInstance ->
                          assertThat(flowNodeInstance.getTotalDurationInMs()).isNotNull())
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
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(deployedInstance.getBpmnProcessId());
              assertThat(savedInstance.getProcessDefinitionVersion())
                  .isEqualTo(String.valueOf(deployedInstance.getVersion()));
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getBusinessKey()).isNull();
              assertThat(savedInstance.getIncidents()).isEmpty();
              assertThat(savedInstance.getVariables()).isEmpty();
              assertThat(savedInstance.getStartDate())
                  .isEqualTo(
                      getExpectedStartDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getEndDate()).isNull();
              assertThat(savedInstance.getDuration()).isNull();
              assertThat(savedInstance.getFlowNodeInstances())
                  .hasSize(2)
                  .containsExactlyInAnyOrder(
                      createFlowNodeInstance(
                          deployedInstance,
                          exportedEvents,
                          START_EVENT,
                          BpmnElementType.START_EVENT),
                      new FlowNodeInstanceDto(
                              String.valueOf(deployedInstance.getBpmnProcessId()),
                              String.valueOf(deployedInstance.getVersion()),
                              ZEEBE_DEFAULT_TENANT_ID,
                              String.valueOf(deployedInstance.getProcessInstanceKey()),
                              USER_TASK,
                              getBpmnElementTypeNameForType(BpmnElementType.USER_TASK),
                              String.valueOf(exportedEvents.get(USER_TASK).get(0).getKey()))
                          .setStartDate(
                              getExpectedStartDateForEvents(exportedEvents.get(USER_TASK)))
                          .setCanceled(false));
            });
  }

  @Test
  public void importCanceledZeebeProcessInstanceData_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createSimpleServiceTaskProcess(processName));

    // We wait for the service task to be exported before cancelling the process
    // (1 * process event, 2 * "start_event" events). Then again for the import of cancellation
    // events (2 cancel events)
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.cancelProcessInstance(deployedInstance.getProcessInstanceKey());
    waitUntilMinimumProcessInstanceEventsExportedCount(6);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
        getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(deployedInstance.getBpmnProcessId());
              assertThat(savedInstance.getProcessDefinitionVersion())
                  .isEqualTo(String.valueOf(deployedInstance.getVersion()));
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getState())
                  .isEqualTo(ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE);
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getBusinessKey()).isNull();
              assertThat(savedInstance.getIncidents()).isEmpty();
              assertThat(savedInstance.getVariables()).isEmpty();
              assertThat(savedInstance.getStartDate())
                  .isEqualTo(
                      getExpectedStartDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getEndDate())
                  .isEqualTo(
                      getExpectedEndDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getDuration())
                  .isEqualTo(
                      getExpectedDurationForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getFlowNodeInstances())
                  .hasSize(2)
                  .containsExactlyInAnyOrder(
                      createFlowNodeInstance(
                          deployedInstance,
                          exportedEvents,
                          START_EVENT,
                          BpmnElementType.START_EVENT),
                      createFlowNodeInstance(
                              deployedInstance,
                              exportedEvents,
                              SERVICE_TASK,
                              BpmnElementType.SERVICE_TASK)
                          .setCanceled(true));
            });
  }

  @Test
  @SneakyThrows
  public void
      importZeebeProcessInstanceDataFromMultipleDays_allDataSavedToOptimizeProcessInstance() {
    // given
    final String processName = "someProcess";
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createSimpleServiceTaskProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.setClock(Instant.now().plus(1, ChronoUnit.DAYS));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK);
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeProcessInstanceRecordDto>> exportedEvents =
        getZeebeExportedProcessInstanceEventsByElementId();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(deployedInstance.getBpmnProcessId());
              assertThat(savedInstance.getProcessDefinitionVersion())
                  .isEqualTo(String.valueOf(deployedInstance.getVersion()));
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getState())
                  .isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getBusinessKey()).isNull();
              assertThat(savedInstance.getIncidents()).isEmpty();
              assertThat(savedInstance.getVariables()).isEmpty();
              assertThat(savedInstance.getStartDate())
                  .isEqualTo(
                      getExpectedStartDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getEndDate())
                  .isEqualTo(
                      getExpectedEndDateForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getDuration())
                  .isEqualTo(
                      getExpectedDurationForEvents(
                          exportedEvents.get(deployedInstance.getBpmnProcessId())));
              assertThat(savedInstance.getFlowNodeInstances())
                  .hasSize(3)
                  .containsExactlyInAnyOrder(
                      createFlowNodeInstance(
                          deployedInstance,
                          exportedEvents,
                          START_EVENT,
                          BpmnElementType.START_EVENT),
                      createFlowNodeInstance(
                          deployedInstance,
                          exportedEvents,
                          SERVICE_TASK,
                          BpmnElementType.SERVICE_TASK),
                      createFlowNodeInstance(
                          deployedInstance, exportedEvents, END_EVENT, BpmnElementType.END_EVENT));
            });
  }

  @Test
  public void importZeebeProcessInstanceData_multipleInstancesForSameProcess() {
    // given
    final String processName = "someProcess";
    final Process deployedProcess =
        zeebeExtension.deployProcess(createStartEndProcess(processName));
    final ProcessInstanceEvent firstInstance =
        zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
    final ProcessInstanceEvent secondInstance =
        zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());

    // when
    // Each instance generates 6 events
    waitUntilMinimumProcessInstanceEventsExportedCount(12);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .hasSize(2)
        .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances()).hasSize(2))
        .extracting(ProcessInstanceDto::getProcessInstanceId)
        .containsExactlyInAnyOrder(
            String.valueOf(firstInstance.getProcessInstanceKey()),
            String.valueOf(secondInstance.getProcessInstanceKey()));
  }

  @Test
  public void importZeebeProcessInstanceData_instancesForDifferentProcesses() {
    // given
    final ProcessInstanceEvent firstInstance =
        zeebeExtension.startProcessInstanceForProcess(
            zeebeExtension.deployProcess(createStartEndProcess("firstProcess")).getBpmnProcessId());
    final ProcessInstanceEvent secondInstance =
        zeebeExtension.startProcessInstanceForProcess(
            zeebeExtension
                .deployProcess(createStartEndProcess("secondProcess"))
                .getBpmnProcessId());

    // when
    // both processes have 6 importable events, wait until all records for both have been exported
    waitUntilMinimumProcessInstanceEventsExportedCount(12);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .hasSize(2)
        .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances()).hasSize(2))
        .extracting(ProcessInstanceDto::getProcessInstanceId)
        .containsExactlyInAnyOrder(
            String.valueOf(firstInstance.getProcessInstanceKey()),
            String.valueOf(secondInstance.getProcessInstanceKey()));
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
    // The first instance generates 6 events, so the 7th indicates that both processes have been
    // exported
    waitUntilMinimumProcessInstanceEventsExportedCount(12);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .hasSize(2)
        .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances()).hasSize(2))
        .extracting(
            ProcessInstanceDto::getProcessInstanceId,
            ProcessInstanceDto::getProcessDefinitionVersion)
        .containsExactlyInAnyOrder(
            Tuple.tuple(String.valueOf(v1Instance.getProcessInstanceKey()), "1"),
            Tuple.tuple(String.valueOf(v2Instance.getProcessInstanceKey()), "2"));
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
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getFlowNodeInstances())
                    .filteredOn(
                        flowNodeInstance -> flowNodeInstance.getFlowNodeId().equals(SERVICE_TASK))
                    .hasSizeGreaterThan(1));
  }

  @Test
  public void importZeebeProcessInstanceData_processStartedDuringProcess() {
    // given
    final String processName = "someProcess";
    final Process process =
        zeebeExtension.deployProcess(createSingleStartDoubleEndEventProcess(processName));
    zeebeExtension.startProcessInstanceBeforeElementWithIds(
        process.getBpmnProcessId(), END_EVENT, END_EVENT_2);

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance -> {
              assertThat(instance.getEndDate()).isNotNull();
              assertThat(instance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
              assertThat(instance.getFlowNodeInstances())
                  .extracting(FlowNodeInstanceDto::getFlowNodeType)
                  .containsExactlyInAnyOrder(
                      BpmnElementType.END_EVENT.getElementTypeName().get(),
                      BpmnElementType.END_EVENT.getElementTypeName().get());
            });
  }

  @Test
  public void importZeebeProcessInstanceData_processContainsTerminateEndEvent() {
    // given
    final String processName = "someProcess";
    deployAndStartInstanceForProcess(createTerminateEndEventProcess(processName));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(6);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getFlowNodeInstances())
                    .extracting(FlowNodeInstanceDto::getFlowNodeType)
                    .containsExactlyInAnyOrder(
                        BpmnElementType.START_EVENT.getElementTypeName().get(),
                        BpmnElementType.END_EVENT.getElementTypeName().get()));
  }

  @Test
  public void importZeebeProcessInstanceData_processContainsInclusiveGateway() {
    // given
    final String processName = "someProcess";
    final Process process =
        zeebeExtension.deployProcess(createInclusiveGatewayProcess(processName));
    zeebeExtension.startProcessInstanceWithVariables(
        process.getBpmnProcessId(), Map.of("varName", "a,b"));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getFlowNodeInstances())
                    .extracting(FlowNodeInstanceDto::getFlowNodeType)
                    .containsExactlyInAnyOrder(
                        BpmnElementType.START_EVENT.getElementTypeName().get(),
                        BpmnElementType.INCLUSIVE_GATEWAY.getElementTypeName().get(),
                        BpmnElementType.END_EVENT.getElementTypeName().get(),
                        BpmnElementType.END_EVENT.getElementTypeName().get()));
  }

  @Test
  public void importSendTaskZeebeProcessInstanceData_flowNodeInstancesCreatedCorrectly() {
    // given
    final ProcessInstanceEvent processInstance =
        deployAndStartInstanceForProcess(createSendTaskProcess("someProcess"));

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getFlowNodeInstances())
                    .hasSize(2)
                    .allSatisfy(
                        flowNodeInstanceDto ->
                            assertThat(flowNodeInstanceDto)
                                .hasFieldOrPropertyWithValue(
                                    FlowNodeInstanceDto.Fields.definitionKey,
                                    processInstance.getBpmnProcessId())
                                .hasFieldOrPropertyWithValue(
                                    FlowNodeInstanceDto.Fields.definitionVersion,
                                    String.valueOf(processInstance.getVersion()))
                                .hasFieldOrPropertyWithValue(
                                    FlowNodeInstanceDto.Fields.tenantId, ZEEBE_DEFAULT_TENANT_ID))
                    .extracting(
                        FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getFlowNodeType)
                    .containsExactlyInAnyOrder(
                        Tuple.tuple(
                            START_EVENT,
                            getBpmnElementTypeNameForType(BpmnElementType.START_EVENT)),
                        Tuple.tuple(
                            SEND_TASK, getBpmnElementTypeNameForType(BpmnElementType.SEND_TASK))));
  }

  @Test
  public void importZeebeProcessInstanceData_processContainsNewBpmnElementsIntroducedWith820() {
    // given a process that contains the following:
    // data stores, date objects, link events, escalation events, undefined tasks
    final BpmnModelInstance model =
        readProcessDiagramAsInstance("/bpmn/compatibility/adventure.bpmn");
    final String processId = zeebeExtension.deployProcess(model).getBpmnProcessId();
    zeebeExtension.startProcessInstanceWithVariables(
        processId, Map.of("space", true, "time", true));

    // when
    waitUntilInstanceRecordWithElementIdExported("milkAdventureEndEventId");
    importAllZeebeEntitiesFromScratch();

    // then all new events were imported
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getFlowNodeInstances())
                    .extracting(FlowNodeInstanceDto::getFlowNodeId)
                    .contains(
                        "linkIntermediateThrowEventId",
                        "linkIntermediateCatchEventId",
                        "undefinedTaskId",
                        "escalationIntermediateThrowEventId",
                        "escalationNonInterruptingBoundaryEventId",
                        "escalationBoundaryEventId",
                        "escalationNonInterruptingStartEventId",
                        "escalationStartEventId",
                        "escalationEndEventId"));
  }

  @DisabledIf("isZeebeVersionPre83")
  @Test
  public void importZeebeProcessInstanceData_processContainsNewBpmnElementsIntroducedWith830() {
    // given a process that contains new signal symbols
    zeebeExtension.deployProcess(createProcessWith83SignalEvents("startSignalName"));
    zeebeExtension.startProcessInstanceWithSignal("startSignalName");

    // when
    waitUntilInstanceRecordWithElementIdExported(SIGNAL_PROCESS_WAIT_FOR_FIRST_SIGNAL_TASK);
    zeebeExtension.broadcastSignalWithName(SIGNAL_PROCESS_FIRST_SIGNAL);
    waitUntilInstanceRecordWithElementIdExported(SIGNAL_PROCESS_WAIT_FOR_SECOND_SIGNAL_TASK);
    zeebeExtension.broadcastSignalWithName(SIGNAL_PROCESS_SECOND_SIGNAL);
    waitUntilInstanceRecordWithElementIdExported(SIGNAL_PROCESS_WAIT_FOR_THIRD_SIGNAL_GATEWAY);
    zeebeExtension.broadcastSignalWithName(SIGNAL_PROCESS_THIRD_SIGNAL);
    waitUntilInstanceRecordWithElementIdExported(SIGNAL_PROCESS_END);

    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getFlowNodeInstances())
                    .extracting(FlowNodeInstanceDto::getFlowNodeId)
                    .contains(
                        SIGNAL_START_EVENT,
                        SIGNAL_START_INT_SUB_PROCESS,
                        SIGNAL_START_NON_INT_SUB_PROCESS,
                        SIGNAL_GATEWAY_CATCH,
                        SIGNAL_THROW,
                        SIGNAL_CATCH,
                        SIGNAL_INTERRUPTING_BOUNDARY,
                        SIGNAL_NON_INTERRUPTING_BOUNDARY,
                        SIGNAL_PROCESS_END));
  }

  @DisabledIf("isZeebeVersionPre85")
  @Test
  public void importZeebeProcessInstanceData_processContainsCompensationTasks() {
    // given
    deployAndStartInstanceForProcess(createCompensationEventProcess());
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK_WITH_COMPENSATION_EVENT);
    zeebeExtension.completeTaskForInstanceWithJobType(COMPENSATION_EVENT_TASK);

    // when
    waitUntilInstanceRecordWithElementTypeAndIntentExported(
        BpmnElementType.BOUNDARY_EVENT, ELEMENT_COMPLETED);
    waitUntilMinimumProcessInstanceEventsExportedCount(12);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance -> {
              assertThat(instance.getFlowNodeInstances())
                  .extracting(FlowNodeInstanceDto::getFlowNodeType)
                  .containsExactlyInAnyOrder(
                      BpmnElementType.END_EVENT.getElementTypeName().get(),
                      BpmnElementType.BOUNDARY_EVENT.getElementTypeName().get(),
                      BpmnElementType.SERVICE_TASK.getElementTypeName().get(),
                      BpmnElementType.START_EVENT.getElementTypeName().get(),
                      BpmnElementType.SERVICE_TASK.getElementTypeName().get());
              assertThat(instance.getFlowNodeInstances())
                  .extracting(FlowNodeInstanceDto::getFlowNodeId)
                  .contains(SERVICE_TASK_WITH_COMPENSATION_EVENT, COMPENSATION_EVENT_TASK);
            });
  }

  // Test backwards compatibility for default tenantID applied when importing records pre multi
  // tenancy introduction
  @DisabledIf("isZeebeVersionWithMultiTenancy")
  @Test
  public void importZeebeProcess_defaultTenantIdForRecordsWithoutTenantId() {
    // given a process deployed before zeebe implemented multi tenancy (pre 8.3.0 this test is
    // disabled)
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));
    waitUntilInstanceRecordWithElementIdExported(START_EVENT);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> instances =
        databaseIntegrationTestExtension.getAllProcessInstances();
    assertThat(instances)
        .extracting(ProcessInstanceDto::getTenantId)
        .singleElement()
        .isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(instances)
        .flatExtracting(ProcessInstanceDto::getFlowNodeInstances)
        .extracting(FlowNodeInstanceDto::getTenantId)
        .hasSize(2)
        .containsOnly(ZEEBE_DEFAULT_TENANT_ID);
  }

  @EnabledIf("isZeebeVersionWithMultiTenancy")
  @Test
  public void importZeebeProcessInstanceData_tenantIdImported() {
    // given
    deployAndStartInstanceForProcess(createStartEndProcess("aProcess"));
    waitUntilInstanceRecordWithElementIdExported(START_EVENT);
    final String expectedTenantId = "testTenant";
    setTenantIdForExportedZeebeRecords(ZEEBE_PROCESS_INSTANCE_INDEX_NAME, expectedTenantId);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> instances =
        databaseIntegrationTestExtension.getAllProcessInstances();
    assertThat(instances)
        .extracting(ProcessInstanceDto::getTenantId)
        .singleElement()
        .isEqualTo(expectedTenantId);
    assertThat(instances)
        .flatExtracting(ProcessInstanceDto::getFlowNodeInstances)
        .extracting(FlowNodeInstanceDto::getTenantId)
        .hasSize(2)
        .containsOnly(expectedTenantId);
  }

  @Test
  public void
      importZeebeProcessInstanceData_documentsHittingNestedDocLimitAreSkippedOnImportIfConfigurationEnabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSkipDataAfterNestedDocLimitReached(true);
    final Map<String, Object> processVariables = Map.of("var1", "someValue1", "var2", "someValue2");
    final Map<String, Object> additionalVariables =
        Map.of("var3", "someValue3", "var4", "someValue4");
    final String processId = "nestedBonanza";
    final Process deployedProcess =
        zeebeExtension.deployProcess(createSimpleServiceTaskProcess(processId));
    final long startedInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), processVariables);
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto firstInstanceOnFirstRoundImport =
        getProcessInstanceForId(String.valueOf(startedInstanceKey));

    // get the current nested document count for first instance
    final int currentNestedDocCount =
        getNestedDocumentCountForProcessInstance(firstInstanceOnFirstRoundImport);
    assertThat(currentNestedDocCount).isGreaterThan(0);

    // update index setting so no more nested documents can be stored
    updateProcessInstanceNestedDocLimit(deployedProcess.getBpmnProcessId(), currentNestedDocCount);

    // Now add additional variables to go beyond the nested document limit
    zeebeExtension.addVariablesToScope(startedInstanceKey, additionalVariables, true);
    waitUntilMinimumVariableDocumentsExportedCount(2);

    // and start a second instance, which should still be imported
    final long secondInstanceKey =
        zeebeExtension.startProcessInstanceWithVariables(
            deployedProcess.getBpmnProcessId(), processVariables);
    waitUntilMinimumProcessInstanceEventsExportedCount(8);

    // when
    importAllZeebeEntitiesFromScratch();

    // then the first instance does not get updated with new nested data
    final ProcessInstanceDto firstInstanceAfterSecondRoundImport =
        getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(firstInstanceAfterSecondRoundImport.getVariables())
        .isEqualTo(firstInstanceOnFirstRoundImport.getVariables());
    // and the second instance can be imported including its nested documents
    assertThat(getProcessInstanceForId(String.valueOf(secondInstanceKey)).getVariables())
        .hasSize(2);
  }

  private FlowNodeInstanceDto createFlowNodeInstance(
      final ProcessInstanceEvent deployedInstance,
      final Map<String, List<ZeebeProcessInstanceRecordDto>> events,
      final String eventId,
      final BpmnElementType eventType) {
    return new FlowNodeInstanceDto(
            String.valueOf(deployedInstance.getBpmnProcessId()),
            String.valueOf(deployedInstance.getVersion()),
            ZEEBE_DEFAULT_TENANT_ID,
            String.valueOf(deployedInstance.getProcessInstanceKey()),
            eventId,
            getBpmnElementTypeNameForType(eventType),
            String.valueOf(events.get(eventId).get(0).getKey()))
        .setStartDate(getExpectedStartDateForEvents(events.get(eventId)))
        .setEndDate(getExpectedEndDateForEvents(events.get(eventId)))
        .setTotalDurationInMs(getExpectedDurationForEvents(events.get(eventId)))
        .setCanceled(false);
  }

  private long getExpectedDurationForEvents(
      final List<ZeebeProcessInstanceRecordDto> eventsForElement) {
    final ZeebeProcessInstanceRecordDto startOfElement =
        eventsForElement.stream()
            .filter(event -> event.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING))
            .findFirst()
            .orElseThrow(eventNotFoundExceptionSupplier);
    final ZeebeProcessInstanceRecordDto endOfElement =
        eventsForElement.stream()
            .filter(
                event ->
                    event.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)
                        || event.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED))
            .findFirst()
            .orElseThrow(eventNotFoundExceptionSupplier);
    return endOfElement.getTimestamp() - startOfElement.getTimestamp();
  }

  private OffsetDateTime getExpectedStartDateForEvents(
      final List<ZeebeProcessInstanceRecordDto> eventsForElement) {
    final ZeebeProcessInstanceRecordDto startOfElement =
        eventsForElement.stream()
            .filter(event -> event.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING))
            .findFirst()
            .orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(startOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  private OffsetDateTime getExpectedEndDateForEvents(
      final List<ZeebeProcessInstanceRecordDto> eventsForElement) {
    final ZeebeProcessInstanceRecordDto endOfElement =
        eventsForElement.stream()
            .filter(
                event ->
                    event.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)
                        || event.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED))
            .findFirst()
            .orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(endOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  private String getBpmnElementTypeNameForType(final BpmnElementType type) {
    return type.getElementTypeName()
        .orElseThrow(() -> new OptimizeRuntimeException("Cannot find name for type: " + type));
  }

  private ProcessInstanceDto getProcessInstanceForId(final String processInstanceId) {
    final List<ProcessInstanceDto> instances =
        databaseIntegrationTestExtension.getAllProcessInstances().stream()
            .filter(instance -> instance.getProcessInstanceId().equals(processInstanceId))
            .collect(Collectors.toList());
    assertThat(instances).hasSize(1);
    return instances.get(0);
  }

  private int getNestedDocumentCountForProcessInstance(final ProcessInstanceDto instance) {
    return instance.getFlowNodeInstances().size()
        + instance.getVariables().size()
        + instance.getIncidents().size();
  }

  @SneakyThrows
  private void updateProcessInstanceNestedDocLimit(
      final String processDefinitionKey, final int nestedDocLimit) {
    databaseIntegrationTestExtension.updateProcessInstanceNestedDocLimit(
        processDefinitionKey, nestedDocLimit, embeddedOptimizeExtension.getConfigurationService());
  }

  private void waitUntilMinimumVariableDocumentsExportedCount(final int minExportedEventCount) {
    final TermsQueryContainer variableBoolQuery = new TermsQueryContainer();
    variableBoolQuery.addTermQuery(
        ZeebeVariableRecordDto.Fields.intent, VariableIntent.CREATED.name());

    waitUntilMinimumDataExportedCount(
        minExportedEventCount, DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME, variableBoolQuery);
  }
}
