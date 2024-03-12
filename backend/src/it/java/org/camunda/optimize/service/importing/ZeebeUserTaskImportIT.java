/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.CLAIM_OPERATION_TYPE;
import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.UNCLAIM_OPERATION_TYPE;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcessWithAssignee;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcessWithCandidateGroups;

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.camunda.optimize.AbstractCCSMIT;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("isZeebeVersionPre85")
public class ZeebeUserTaskImportIT extends AbstractCCSMIT {

  private static final String TEST_PROCESS = "aProcess";
  private static final String DUE_DATE = "2023-11-01T12:00:00+05:00";
  private static final String ASSIGNEE_ID = "assigneeId";
  private static final String CANDIDATE_GROUP = "candidateGroup";

  @Test
  public void importRunningZeebeUserTaskData() {
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(
                      createRunningUserTaskInstance(instance, exportedEvents)
                          .setDueDate(OffsetDateTime.parse(DUE_DATE)));
            });
  }

  @Test
  public void importCompletedUnclaimedZeebeUserTaskData_viaWriter() {
    // import all data for completed usertask (creation and completion) in one batch, hence the
    // upsert inserts the new instance
    // created with the logic in the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    // TODO #11579 manual record manipulation will be removed in favour of using zeebeClient once
    // functionality is available
    updateCreatedUserTaskRecordToSimulateCompletion();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> userTaskEvents = getZeebeExportedUserTaskEvents();
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCompletedUserTaskEvents(userTaskEvents);
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, userTaskEvents);
    expectedUserTask
        .setDueDate(OffsetDateTime.parse(DUE_DATE))
        .setEndDate(expectedEndDate)
        .setIdleDurationInMs(0L)
        .setTotalDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis())
        .setWorkDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis());

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only the userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importCompletedUnclaimedZeebeUserTaskData_viaUpdateScript() {
    // import completed userTask data after the first userTask record was already imported, hence
    // the upsert uses the logic
    // from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));

    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    final List<ZeebeUserTaskRecordDto> runningUserTaskEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, runningUserTaskEvents);
    // fake userTask completion record
    // TODO #11579 manual record manipulation will be removed in favour of using zeebeClient once
    // functionality is available
    updateCreatedUserTaskRecordToSimulateCompletion();

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final Map<String, List<ZeebeUserTaskRecordDto>> completedUserTaskEvents =
        getZeebeExportedUserTaskEventsByElementId();
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCompletedUserTaskEvents(completedUserTaskEvents.get(USER_TASK));
    expectedUserTask
        .setDueDate(OffsetDateTime.parse(DUE_DATE))
        .setEndDate(expectedEndDate)
        .setIdleDurationInMs(0L)
        .setTotalDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis())
        .setWorkDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis());

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only the userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importCanceledUnclaimedZeebeUserTaskData_viaWriter() {
    // import all data for canceled usertask (creation and cancellation) in one batch, hence the
    // upsert inserts the new instance
    // created with the logic in the writer
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithElementIdAndIntentExported(
        USER_TASK, UserTaskIntent.CANCELED.name());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then the import in one batch correctly set all fields in the new instance document
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEvents);
    final Long expectedTotalAndIdleDuration =
        Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis();
    expectedUserTask
        .setDueDate(OffsetDateTime.parse(DUE_DATE))
        .setEndDate(expectedEndDate)
        .setTotalDurationInMs(expectedTotalAndIdleDuration)
        .setIdleDurationInMs(expectedTotalAndIdleDuration)
        .setWorkDurationInMs(0L)
        .setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importCanceledUnclaimedZeebeUserTaskData_viaUpdateScript() {
    // import canceled userTask data after the first userTask record was already imported, hence the
    // upsert uses the logic
    // from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithElementIdAndIntentExported(
        USER_TASK, UserTaskIntent.CANCELED.name());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then the import over two batches correctly updates all fields with the update script
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEvents);
    expectedUserTask
        .setDueDate(OffsetDateTime.parse(DUE_DATE))
        .setEndDate(expectedEndDate)
        .setTotalDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis())
        .setIdleDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis())
        .setWorkDurationInMs(0L)
        .setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importCanceledClaimedZeebeUserTaskData_viaWriter() {
    // import all data for canceled usertask in one batch, hence the upsert inserts the new instance
    // created with the logic in the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    updateCreatedUserTaskRecordToSimulateAssign(ASSIGNEE_ID);
    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithElementIdAndIntentExported(
        USER_TASK, UserTaskIntent.CANCELED.name());
    updateCancelUserTaskRecordToSimulateAssignee();
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEvents);
    final OffsetDateTime assignDate = getTimestampForAssignedUserTaskEvents(exportedEvents);
    expectedUserTask
        .setAssignee(ASSIGNEE_ID)
        .setDueDate(OffsetDateTime.parse(DUE_DATE))
        .setEndDate(expectedEndDate)
        .setTotalDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis())
        .setIdleDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), assignDate).toMillis())
        .setWorkDurationInMs(Duration.between(assignDate, expectedEndDate).toMillis())
        .setAssigneeOperations(
            List.of(
                new AssigneeOperationDto()
                    .setId(getExpectedIdFromRecords(exportedEvents, ASSIGNED))
                    .setOperationType(CLAIM_OPERATION_TYPE.toString())
                    .setUserId(ASSIGNEE_ID)
                    .setTimestamp(getTimestampForAssignedUserTaskEvents(exportedEvents))))
        .setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importCanceledClaimedZeebeUserTaskData_viaUpdateScript() {
    // import canceled userTask data after the first userTask records were already imported, hence
    // the upsert uses the logic
    // from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    updateCreatedUserTaskRecordToSimulateAssign(ASSIGNEE_ID);
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithElementIdAndIntentExported(
        USER_TASK, UserTaskIntent.CANCELED.name());
    updateCancelUserTaskRecordToSimulateAssignee();
    waitUntilUserTaskRecordWithElementIdAndIntentExported(
        USER_TASK, UserTaskIntent.CANCELED.name());
    updateCancelUserTaskRecordToSimulateAssignee();
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEvents);
    final OffsetDateTime assignDate = getTimestampForAssignedUserTaskEvents(exportedEvents);
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    expectedUserTask
        .setAssignee(ASSIGNEE_ID)
        .setDueDate(OffsetDateTime.parse(DUE_DATE))
        .setEndDate(expectedEndDate)
        .setTotalDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis())
        .setIdleDurationInMs(
            Duration.between(expectedUserTask.getStartDate(), assignDate).toMillis())
        .setWorkDurationInMs(Duration.between(assignDate, expectedEndDate).toMillis())
        .setAssigneeOperations(
            List.of(
                new AssigneeOperationDto()
                    .setId(getExpectedIdFromRecords(exportedEvents, ASSIGNED))
                    .setOperationType(CLAIM_OPERATION_TYPE.toString())
                    .setUserId(ASSIGNEE_ID)
                    .setTimestamp(getTimestampForAssignedUserTaskEvents(exportedEvents))))
        .setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importClaimOperation_viaWriter() {
    // import assignee usertask operations in one batch, hence the upsert inserts the new instance
    // created with the logic in
    // the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // Manually add usertask assign record
    // TODO #11579 manual record manipulation will be removed in favour of using zeebeClient once
    // functionality is available
    updateCreatedUserTaskRecordToSimulateAssign(ASSIGNEE_ID);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents)
            .setIdleDurationInMs(1000L)
            .setAssignee(ASSIGNEE_ID)
            .setAssigneeOperations(
                List.of(
                    new AssigneeOperationDto()
                        .setId(getExpectedIdFromRecords(exportedEvents, ASSIGNED))
                        .setUserId(ASSIGNEE_ID)
                        .setOperationType(CLAIM_OPERATION_TYPE.toString())
                        .setTimestamp(
                            getTimestampForZeebeAssignEvents(exportedEvents, ASSIGNEE_ID))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importClaimOperation_viaUpdateScript() {
    // import assignee userTask data after the first userTask records were already imported, hence
    // the upsert uses the logic
    // from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    // remove all zeebe records except userTask ones to test userTask import only
    // Manually add usertask assign record.
    // TODO #11579 manual record manipulation will be removed in favour of using zeebeClient once
    // functionality is available
    updateCreatedUserTaskRecordToSimulateAssign(ASSIGNEE_ID);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents)
            .setIdleDurationInMs(1000L)
            .setAssignee(ASSIGNEE_ID)
            .setAssigneeOperations(
                List.of(
                    new AssigneeOperationDto()
                        .setId(getExpectedIdFromRecords(exportedEvents, ASSIGNED))
                        .setUserId(ASSIGNEE_ID)
                        .setOperationType(CLAIM_OPERATION_TYPE.toString())
                        .setTimestamp(
                            getTimestampForZeebeAssignEvents(exportedEvents, ASSIGNEE_ID))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importUnclaimOperation_viaWriter() {
    // import assignee usertask operations in one batch, hence the upsert inserts the new instance
    // created with the logic in
    // the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, null, ASSIGNEE_ID));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // Manually add usertaskunassign record.
    // TODO #11579 manual record manipulation will be removed in favour of using zeebeClient once
    // functionality is available
    updateCreatedUserTaskRecordToSimulateAssign("");
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(
                      createRunningUserTaskInstance(instance, exportedEvents)
                          .setIdleDurationInMs(0L)
                          .setWorkDurationInMs(1000L)
                          .setAssigneeOperations(
                              List.of(
                                  new AssigneeOperationDto()
                                      .setId(getExpectedIdFromRecords(exportedEvents, CREATING))
                                      .setUserId(ASSIGNEE_ID)
                                      .setOperationType(CLAIM_OPERATION_TYPE.toString())
                                      .setTimestamp(
                                          getExpectedStartDateForUserTaskEvents(exportedEvents)),
                                  new AssigneeOperationDto()
                                      .setId(getExpectedIdFromRecords(exportedEvents, ASSIGNED))
                                      .setOperationType(UNCLAIM_OPERATION_TYPE.toString())
                                      .setTimestamp(
                                          getTimestampForAssignedUserTaskEvents(exportedEvents)))));
            });
  }

  @Test
  public void importUnclaimOperation_viaUpdateScript() {
    // import assignee userTask data after the first userTask records were already imported, hence
    // the upsert uses the logic
    // from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, null, ASSIGNEE_ID));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    // Manually add usertask assign record.
    // TODO #11579 manual record manipulation will be removed in favour of using zeebeClient once
    // functionality is available
    updateCreatedUserTaskRecordToSimulateAssign("");

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents)
            .setIdleDurationInMs(0L)
            .setWorkDurationInMs(1000L)
            .setAssigneeOperations(
                List.of(
                    new AssigneeOperationDto()
                        .setId(getExpectedIdFromRecords(exportedEvents, CREATING))
                        .setUserId(ASSIGNEE_ID)
                        .setOperationType(CLAIM_OPERATION_TYPE.toString())
                        .setTimestamp(getExpectedStartDateForUserTaskEvents(exportedEvents)),
                    new AssigneeOperationDto()
                        .setId(getExpectedIdFromRecords(exportedEvents, ASSIGNED))
                        .setOperationType(UNCLAIM_OPERATION_TYPE.toString())
                        .setTimestamp(getTimestampForZeebeAssignEvents(exportedEvents, ""))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTask);
            });
  }

  @Test
  public void importAssignee_fromCreationRecord() {
    // given a process that was started with an assignee already present in the model
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, DUE_DATE, ASSIGNEE_ID));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(
                      createRunningUserTaskInstance(instance, exportedEvents)
                          .setDueDate(OffsetDateTime.parse(DUE_DATE))
                          .setIdleDurationInMs(0L)
                          .setAssignee(ASSIGNEE_ID)
                          .setAssigneeOperations(
                              List.of(
                                  new AssigneeOperationDto()
                                      .setId(getExpectedIdFromRecords(exportedEvents, CREATING))
                                      .setUserId(ASSIGNEE_ID)
                                      .setOperationType(CLAIM_OPERATION_TYPE.toString())
                                      .setTimestamp(
                                          getExpectedStartDateForUserTaskEvents(exportedEvents)))));
            });
  }

  @Test
  public void importUpdateCandidateGroupRecord_viaWriter() {
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    updateCreatedUserTaskRecordToSimulateUpdate();
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .isEqualTo(
                      createRunningUserTaskInstance(instance, exportedEvents)
                          .setDueDate(OffsetDateTime.parse(DUE_DATE))
                          .setCandidateGroups(List.of(CANDIDATE_GROUP)));
            });
  }

  @Test
  public void importUpdateCandidateGroupRecord_viaUpdateScript() {
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    updateCreatedUserTaskRecordToSimulateUpdate();

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .isEqualTo(
                      createRunningUserTaskInstance(instance, exportedEvents)
                          .setDueDate(OffsetDateTime.parse(DUE_DATE))
                          .setCandidateGroups(List.of(CANDIDATE_GROUP)));
            });
  }

  @Test
  public void importCandidateGroup_fromCreationRecord() {
    // given a process that was started with a candidateGroup already present in the model
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithCandidateGroups(
                TEST_PROCESS, DUE_DATE, CANDIDATE_GROUP));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instance.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .isEqualTo(
                      createRunningUserTaskInstance(instance, exportedEvents)
                          .setDueDate(OffsetDateTime.parse(DUE_DATE))
                          .setCandidateGroups(List.of(CANDIDATE_GROUP)));
            });
  }

  private FlowNodeInstanceDto createRunningUserTaskInstance(
      final ProcessInstanceEvent deployedInstance, final List<ZeebeUserTaskRecordDto> events) {
    return new FlowNodeInstanceDto()
        .setFlowNodeInstanceId(String.valueOf(events.get(0).getValue().getElementInstanceKey()))
        .setFlowNodeId(USER_TASK)
        .setFlowNodeType(FLOW_NODE_TYPE_USER_TASK)
        .setProcessInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
        .setDefinitionKey(String.valueOf(deployedInstance.getBpmnProcessId()))
        .setDefinitionVersion(String.valueOf(deployedInstance.getVersion()))
        .setTenantId(ZEEBE_DEFAULT_TENANT_ID)
        .setUserTaskInstanceId(getExpectedUserTaskInstanceIdFromRecords(events))
        .setStartDate(getExpectedStartDateForUserTaskEvents(events))
        .setCanceled(false);
  }

  private OffsetDateTime getExpectedStartDateForUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForZeebeEventsWithIntent(eventsForElement, UserTaskIntent.CREATING);
  }

  private OffsetDateTime getExpectedEndDateForCompletedUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForZeebeEventsWithIntent(eventsForElement, UserTaskIntent.COMPLETED);
  }

  private OffsetDateTime getTimestampForAssignedUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForZeebeEventsWithIntent(eventsForElement, ASSIGNED);
  }

  private OffsetDateTime getExpectedEndDateForCanceledUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForZeebeEventsWithIntent(eventsForElement, UserTaskIntent.CANCELED);
  }

  private String getExpectedUserTaskInstanceIdFromRecords(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return eventsForElement.stream()
        .findFirst()
        .map(ZeebeUserTaskRecordDto::getValue)
        .map(ZeebeUserTaskDataDto::getUserTaskKey)
        .map(String::valueOf)
        .orElseThrow(eventNotFoundExceptionSupplier);
  }

  private String getExpectedIdFromRecords(
      final List<ZeebeUserTaskRecordDto> eventsForElement, final UserTaskIntent intent) {
    return eventsForElement.stream()
        .filter(event -> intent.equals(event.getIntent()))
        .findFirst()
        .map(ZeebeUserTaskRecordDto::getKey)
        .map(String::valueOf)
        .orElseThrow(eventNotFoundExceptionSupplier);
  }

  private void removeAllZeebeExportRecordsExceptUserTaskRecords() {
    databaseIntegrationTestExtension.deleteAllOtherZeebeRecordsWithPrefix(
        zeebeExtension.getZeebeRecordPrefix(), ZEEBE_USER_TASK_INDEX_NAME);
  }

  private void updateCreatedUserTaskRecordToSimulateCompletion() {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        ZEEBE_USER_TASK_INDEX_NAME,
        """
            if (ctx._source.intent == "CREATED") {
              ctx._source.intent = "COMPLETED";
              ctx._source.timestamp = ctx._source.timestamp + 1000; // this will be the userTask endDate
              ctx._source.sequence = ctx._source.sequence + 10;
            }
            """);
  }

  private void updateCreatedUserTaskRecordToSimulateAssign(final String assigneeId) {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        ZEEBE_USER_TASK_INDEX_NAME,
        """
            if (ctx._source.intent == "CREATED") {
              ctx._source.intent = "ASSIGNED";
              ctx._source.timestamp = ctx._source.timestamp + 1000;
              ctx._source.sequence = ctx._source.sequence + 1;
              ctx._source.value.assignee = "%s";
            }
            """
            .formatted(assigneeId));
  }

  private void updateCreatedUserTaskRecordToSimulateUpdate() {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        ZEEBE_USER_TASK_INDEX_NAME,
        """
            if (ctx._source.intent == "CREATED") {
              ctx._source.intent = "UPDATED";
              ctx._source.timestamp = ctx._source.timestamp + 1000;
              ctx._source.sequence = ctx._source.sequence + 1;
              ctx._source.value.candidateGroupsList = ["%s"];
              ctx._source.value.changedAttributes = ["candidateGroupsList"];
            }
            """
            .formatted(CANDIDATE_GROUP));
  }

  private void updateCancelUserTaskRecordToSimulateAssignee() {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        ZEEBE_USER_TASK_INDEX_NAME,
        """
            if (ctx._source.intent == "CANCELED") {
              ctx._source.value.assignee = "%s";
            }
            """
            .formatted(ASSIGNEE_ID));
  }

  private List<ZeebeUserTaskRecordDto> getZeebeExportedUserTaskEvents() {
    return getZeebeExportedUserTaskEventsByElementId().get(USER_TASK);
  }
}
