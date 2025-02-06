/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType.CLAIM_OPERATION_TYPE;
import static io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType.UNCLAIM_OPERATION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static io.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcessWithAssignee;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcessWithCandidateGroup;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CANCELED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType;
import io.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("isZeebeVersionPre86")
public class ZeebeUserTaskImportIT extends AbstractCCSMIT {

  private static final String TEST_PROCESS = "aProcess";
  private static final String DUE_DATE = "2024-07-24T00:00Z[GMT]";
  private static final OffsetDateTime EXPECTED_DUE_DATE = OffsetDateTime.parse("2024-07-24T00:00Z");
  private static final String ASSIGNEE_ID = "assigneeId";

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
              final FlowNodeInstanceDto runningUserTaskInstance =
                  createRunningUserTaskInstance(instance, exportedEvents);
              runningUserTaskInstance.setDueDate(EXPECTED_DUE_DATE);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(runningUserTaskInstance);
            });
  }

  @Test
  public void importCompletedUnclaimedZeebeUserTaskData_viaWriter() {
    // import all data for completed usertask (creation and completion) in one batch, hence the
    // upsert inserts the new instance created with the logic in the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    List<ZeebeUserTaskRecordDto> userTaskEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.completeZeebeUserTask(getExpectedUserTaskInstanceIdFromRecords(userTaskEvents));
    waitUntilUserTaskRecordWithIntentExported(COMPLETED);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    userTaskEvents = getZeebeExportedUserTaskEvents();
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCompletedUserTaskEvents(userTaskEvents);
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, userTaskEvents);
    expectedUserTask.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTask.setEndDate(expectedEndDate);
    expectedUserTask.setIdleDurationInMs(0L);
    expectedUserTask.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEvents));
    expectedUserTask.setWorkDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEvents));

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
    // the upsert uses the logic from the update script
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
    List<ZeebeUserTaskRecordDto> userTaskEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.completeZeebeUserTask(getExpectedUserTaskInstanceIdFromRecords(userTaskEvents));
    waitUntilUserTaskRecordWithIntentExported(COMPLETED);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    userTaskEvents = getZeebeExportedUserTaskEvents();
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCompletedUserTaskEvents(userTaskEvents);
    expectedUserTask.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTask.setEndDate(expectedEndDate);
    expectedUserTask.setIdleDurationInMs(0L);
    expectedUserTask.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEvents));
    expectedUserTask.setWorkDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEvents));

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
    waitUntilUserTaskRecordWithIntentExported(UserTaskIntent.CANCELED);
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
    expectedUserTask.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTask.setEndDate(expectedEndDate);
    expectedUserTask.setTotalDurationInMs(expectedTotalAndIdleDuration);
    expectedUserTask.setIdleDurationInMs(expectedTotalAndIdleDuration);
    expectedUserTask.setWorkDurationInMs(0L);
    expectedUserTask.setCanceled(true);

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
    // upsert uses the logic  from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExported(UserTaskIntent.CANCELED);
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
    expectedUserTask.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTask.setEndDate(expectedEndDate);
    expectedUserTask.setTotalDurationInMs(
        Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis());
    expectedUserTask.setIdleDurationInMs(
        Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis());
    expectedUserTask.setWorkDurationInMs(0L);
    expectedUserTask.setCanceled(true);

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
    List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEvents), ASSIGNEE_ID);
    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExported(CANCELED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEvents);
    final OffsetDateTime assignDate = getTimestampForAssignedUserTaskEvents(exportedEvents);
    expectedUserTask.setAssignee(ASSIGNEE_ID);
    expectedUserTask.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTask.setEndDate(expectedEndDate);
    expectedUserTask.setTotalDurationInMs(
        Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis());
    expectedUserTask.setIdleDurationInMs(
        Duration.between(expectedUserTask.getStartDate(), assignDate).toMillis());
    expectedUserTask.setWorkDurationInMs(Duration.between(assignDate, expectedEndDate).toMillis());
    expectedUserTask.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEvents, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getTimestampForAssignedUserTaskEvents(exportedEvents))));
    expectedUserTask.setCanceled(true);

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
    // the upsert uses the logic from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEvents), ASSIGNEE_ID);
    waitUntilUserTaskRecordWithIntentExported(ASSIGNED);

    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    zeebeExtension.cancelProcessInstance(instance.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExported(UserTaskIntent.CANCELED);
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    exportedEvents = getZeebeExportedUserTaskEvents();
    final OffsetDateTime expectedEndDate =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEvents);
    final OffsetDateTime assignDate = getTimestampForAssignedUserTaskEvents(exportedEvents);
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    expectedUserTask.setAssignee(ASSIGNEE_ID);
    expectedUserTask.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTask.setEndDate(expectedEndDate);
    expectedUserTask.setTotalDurationInMs(
        Duration.between(expectedUserTask.getStartDate(), expectedEndDate).toMillis());
    expectedUserTask.setIdleDurationInMs(
        Duration.between(expectedUserTask.getStartDate(), assignDate).toMillis());
    expectedUserTask.setWorkDurationInMs(Duration.between(assignDate, expectedEndDate).toMillis());
    expectedUserTask.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEvents, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getTimestampForAssignedUserTaskEvents(exportedEvents))));
    expectedUserTask.setCanceled(true);

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
    // created with the logic in the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEvents), ASSIGNEE_ID);
    waitUntilUserTaskRecordWithIntentExported(ASSIGNED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    expectedUserTask.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEvents));
    expectedUserTask.setAssignee(ASSIGNEE_ID);
    expectedUserTask.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEvents, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
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
    // the upsert uses the logic from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEvents), ASSIGNEE_ID);
    waitUntilUserTaskRecordWithIntentExported(ASSIGNED);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    expectedUserTask.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEvents));
    expectedUserTask.setAssignee(ASSIGNEE_ID);
    expectedUserTask.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEvents, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
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
    // created with the logic in the writer
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, null, ASSIGNEE_ID));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);

    if (isZeebeVersion87OrLater()) {
      // to wait for `ASSIGNED` event triggered by Zeebe after UT creation with the defined
      // `assignee`
      waitUntilUserTaskRecordWithIntentExported(1, ASSIGNED);
      zeebeExtension.unassignUserTask(
          getExpectedUserTaskInstanceIdFromRecords(getZeebeExportedUserTaskEvents()));
      // wait for the 2nd `ASSIGNED` event triggered by UT unassign operation
      waitUntilUserTaskRecordWithIntentExported(2, ASSIGNED);
    } else {
      zeebeExtension.unassignUserTask(
          getExpectedUserTaskInstanceIdFromRecords(getZeebeExportedUserTaskEvents()));
      waitUntilUserTaskRecordWithIntentExported(ASSIGNED);
    }

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
              final FlowNodeInstanceDto runningUserTaskInstance =
                  createRunningUserTaskInstance(instance, exportedEvents);
              runningUserTaskInstance.setIdleDurationInMs(0L);
              runningUserTaskInstance.setWorkDurationInMs(
                  isZeebeVersion87OrLater()
                      ? getDurationInMsBetweenStartDateAndLastAssignedOperation(exportedEvents)
                      : getDurationInMsBetweenStartAndFirstAssignOperation(exportedEvents));
              runningUserTaskInstance.setAssigneeOperations(
                  List.of(
                      createAssigneeOperationDto(
                          getExpectedIdFromRecords(exportedEvents, CREATING),
                          CLAIM_OPERATION_TYPE,
                          ASSIGNEE_ID,
                          getExpectedStartDateForUserTaskEvents(exportedEvents)),
                      createAssigneeOperationDto(
                          getExpectedIdFromRecords(exportedEvents, ASSIGNED),
                          UNCLAIM_OPERATION_TYPE,
                          null,
                          getTimestampForLastZeebeEventsWithIntent(exportedEvents, ASSIGNED))));
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(runningUserTaskInstance);
            });
  }

  @Test
  public void importUnclaimOperation_viaUpdateScript() {
    // import assignee userTask data after the first userTask records were already imported, hence
    // the upsert uses the logic from the update script
    // given
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, null, ASSIGNEE_ID));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();

    if (isZeebeVersion87OrLater()) {
      // to wait for `ASSIGNED` event triggered by Zeebe after UT creation with the defined
      // `assignee`
      waitUntilUserTaskRecordWithIntentExported(1, ASSIGNED);
      zeebeExtension.unassignUserTask(
          getExpectedUserTaskInstanceIdFromRecords(getZeebeExportedUserTaskEvents()));
      // wait for the 2nd `ASSIGNED` event triggered by UT unassign operation
      waitUntilUserTaskRecordWithIntentExported(2, ASSIGNED);
    } else {
      zeebeExtension.unassignUserTask(getExpectedUserTaskInstanceIdFromRecords(exportedEvents));
      waitUntilUserTaskRecordWithIntentExported(ASSIGNED);
    }

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto expectedUserTask =
        createRunningUserTaskInstance(instance, exportedEvents);
    expectedUserTask.setIdleDurationInMs(0L);
    expectedUserTask.setWorkDurationInMs(
        isZeebeVersion87OrLater()
            ? getDurationInMsBetweenStartDateAndLastAssignedOperation(exportedEvents)
            : getDurationInMsBetweenStartAndFirstAssignOperation(exportedEvents));
    expectedUserTask.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEvents, CREATING),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getExpectedStartDateForUserTaskEvents(exportedEvents)),
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEvents, ASSIGNED),
                UNCLAIM_OPERATION_TYPE,
                null,
                isZeebeVersion87OrLater()
                    ? getTimestampForZeebeLastAssignedEvents(exportedEvents, "")
                    : getTimestampForZeebeUnassignEvent(exportedEvents))));

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
              final FlowNodeInstanceDto runningUserTaskInstance =
                  createRunningUserTaskInstance(instance, exportedEvents);
              runningUserTaskInstance.setDueDate(EXPECTED_DUE_DATE);
              runningUserTaskInstance.setIdleDurationInMs(0L);
              runningUserTaskInstance.setAssignee(ASSIGNEE_ID);
              runningUserTaskInstance.setAssigneeOperations(
                  List.of(
                      createAssigneeOperationDto(
                          getExpectedIdFromRecords(exportedEvents, CREATING),
                          CLAIM_OPERATION_TYPE,
                          ASSIGNEE_ID,
                          getExpectedStartDateForUserTaskEvents(exportedEvents))));
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(runningUserTaskInstance);
            });
  }

  @Test
  public void importMultipleAssigneeOperations_viaWriter() {
    // given
    final String assigneeId1 = ASSIGNEE_ID + "1";
    final String assigneeId2 = ASSIGNEE_ID + "2";
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    final List<ZeebeUserTaskRecordDto> userTaskEvents = getZeebeExportedUserTaskEvents();
    final long userTaskInstanceId = getExpectedUserTaskInstanceIdFromRecords(userTaskEvents);
    zeebeExtension.assignUserTask(userTaskInstanceId, assigneeId1);
    zeebeExtension.unassignUserTask(userTaskInstanceId);
    zeebeExtension.assignUserTask(userTaskInstanceId, assigneeId2);
    zeebeExtension.completeZeebeUserTask(userTaskInstanceId);
    waitUntilUserTaskRecordWithIntentExported(COMPLETED);

    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto runningUserTaskInstance =
        createRunningUserTaskInstance(instance, exportedEvents);
    runningUserTaskInstance.setEndDate(
        getExpectedEndDateForCompletedUserTaskEvents(exportedEvents));
    runningUserTaskInstance.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEvents)
            + getDurationInMsBetweenAssignOperations(exportedEvents, "", assigneeId2));
    runningUserTaskInstance.setWorkDurationInMs(
        getDurationInMsBetweenAssignOperations(exportedEvents, assigneeId1, "")
            + getDurationInMsBetweenLastAssignOperationAndEnd(exportedEvents, assigneeId2));
    runningUserTaskInstance.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(exportedEvents));
    runningUserTaskInstance.setAssignee(assigneeId2);
    runningUserTaskInstance.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEvents, assigneeId1),
                CLAIM_OPERATION_TYPE,
                assigneeId1,
                getTimestampForZeebeAssignEvents(exportedEvents, assigneeId1)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEvents, ""),
                UNCLAIM_OPERATION_TYPE,
                null,
                getTimestampForZeebeUnassignEvent(exportedEvents)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEvents, assigneeId2),
                CLAIM_OPERATION_TYPE,
                assigneeId2,
                getTimestampForZeebeAssignEvents(exportedEvents, assigneeId2))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getFlowNodeInstances())
                    .singleElement()
                    .usingRecursiveComparison()
                    .isEqualTo(runningUserTaskInstance));
  }

  @Test
  public void importMultipleAssigneeOperations_viaUpdateScript() {
    // given
    final String assigneeId1 = ASSIGNEE_ID + "1";
    final String assigneeId2 = ASSIGNEE_ID + "2";
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    final List<ZeebeUserTaskRecordDto> userTaskEvents = getZeebeExportedUserTaskEvents();
    final long userTaskInstanceId = getExpectedUserTaskInstanceIdFromRecords(userTaskEvents);
    zeebeExtension.assignUserTask(userTaskInstanceId, assigneeId1);
    waitUntilUserTaskRecordWithIntentExported(ASSIGNED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    zeebeExtension.unassignUserTask(userTaskInstanceId);
    zeebeExtension.assignUserTask(userTaskInstanceId, assigneeId2);
    zeebeExtension.completeZeebeUserTask(userTaskInstanceId);
    waitUntilUserTaskRecordWithIntentExported(COMPLETED);

    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    final FlowNodeInstanceDto runningUserTaskInstance =
        createRunningUserTaskInstance(instance, exportedEvents);
    runningUserTaskInstance.setEndDate(
        getExpectedEndDateForCompletedUserTaskEvents(exportedEvents));
    runningUserTaskInstance.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEvents)
            + getDurationInMsBetweenAssignOperations(exportedEvents, "", assigneeId2));
    runningUserTaskInstance.setWorkDurationInMs(
        getDurationInMsBetweenAssignOperations(exportedEvents, assigneeId1, "")
            + getDurationInMsBetweenLastAssignOperationAndEnd(exportedEvents, assigneeId2));
    runningUserTaskInstance.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(exportedEvents));
    runningUserTaskInstance.setAssignee(assigneeId2);
    runningUserTaskInstance.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEvents, assigneeId1),
                CLAIM_OPERATION_TYPE,
                assigneeId1,
                getTimestampForZeebeAssignEvents(exportedEvents, assigneeId1)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEvents, ""),
                UNCLAIM_OPERATION_TYPE,
                null,
                getTimestampForZeebeUnassignEvent(exportedEvents)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEvents, assigneeId2),
                CLAIM_OPERATION_TYPE,
                assigneeId2,
                getTimestampForZeebeAssignEvents(exportedEvents, assigneeId2))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getFlowNodeInstances())
                    .singleElement()
                    .usingRecursiveComparison()
                    .isEqualTo(runningUserTaskInstance));
  }

  @Test
  public void doNotImportCandidateGroupUpdates() {
    // given
    deployAndStartInstanceForProcess(
        createSimpleNativeUserTaskProcessWithCandidateGroup(
            TEST_PROCESS, DUE_DATE, "aCandidateGroup"));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    final List<ZeebeUserTaskRecordDto> exportedEvents = getZeebeExportedUserTaskEvents();
    zeebeExtension.updateCandidateGroupForUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEvents), "anotherCandidateGroup");

    // when
    importAllZeebeEntitiesFromScratch();

    // then no candidate group data was imported
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .flatExtracting(ProcessInstanceDto::getFlowNodeInstances)
        .extracting(FlowNodeInstanceDto::getCandidateGroups)
        .containsOnly(Collections.emptyList());
  }

  @Test
  public void importOtherDueDateFormat() {
    // given
    final String dueDateStringInOtherFormat = "2023-03-02T15:35+02:00";
    final ProcessInstanceEvent instance =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcess(TEST_PROCESS, dueDateStringInOtherFormat));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when
    importAllZeebeEntitiesFromScratch();

    // then dueDate is correctly parsed
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getFlowNodeInstances())
                    .singleElement()
                    .extracting(FlowNodeInstanceDto::getDueDate)
                    .isEqualTo(OffsetDateTime.parse(dueDateStringInOtherFormat)));
  }

  private FlowNodeInstanceDto createRunningUserTaskInstance(
      final ProcessInstanceEvent deployedInstance, final List<ZeebeUserTaskRecordDto> events) {
    final FlowNodeInstanceDto flowNodeInstanceDto = new FlowNodeInstanceDto();
    flowNodeInstanceDto.setFlowNodeInstanceId(
        String.valueOf(events.get(0).getValue().getElementInstanceKey()));
    flowNodeInstanceDto.setFlowNodeId(USER_TASK);
    flowNodeInstanceDto.setFlowNodeType(FLOW_NODE_TYPE_USER_TASK);
    flowNodeInstanceDto.setProcessInstanceId(
        String.valueOf(deployedInstance.getProcessInstanceKey()));
    flowNodeInstanceDto.setDefinitionKey(String.valueOf(deployedInstance.getBpmnProcessId()));
    flowNodeInstanceDto.setDefinitionVersion(String.valueOf(deployedInstance.getVersion()));
    flowNodeInstanceDto.setTenantId(ZEEBE_DEFAULT_TENANT_ID);
    flowNodeInstanceDto.setUserTaskInstanceId(
        String.valueOf(getExpectedUserTaskInstanceIdFromRecords(events)));
    flowNodeInstanceDto.setStartDate(getExpectedStartDateForUserTaskEvents(events));
    flowNodeInstanceDto.setCanceled(false);
    return flowNodeInstanceDto;
  }

  private OffsetDateTime getExpectedStartDateForUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForFirstZeebeEventsWithIntent(eventsForElement, UserTaskIntent.CREATING);
  }

  private OffsetDateTime getExpectedEndDateForCompletedUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForFirstZeebeEventsWithIntent(eventsForElement, COMPLETED);
  }

  private OffsetDateTime getTimestampForAssignedUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForFirstZeebeEventsWithIntent(eventsForElement, ASSIGNED);
  }

  private OffsetDateTime getExpectedEndDateForCanceledUserTaskEvents(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForFirstZeebeEventsWithIntent(eventsForElement, UserTaskIntent.CANCELED);
  }

  private long getExpectedTotalDurationForCompletedUserTask(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return Duration.between(
            getExpectedStartDateForUserTaskEvents(eventsForElement),
            getExpectedEndDateForCompletedUserTaskEvents(eventsForElement))
        .toMillis();
  }

  private long getDurationInMsBetweenStartAndFirstAssignOperation(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return Duration.between(
            getExpectedStartDateForUserTaskEvents(eventsForElement),
            getTimestampForAssignedUserTaskEvents(eventsForElement))
        .toMillis();
  }

  private long getDurationInMsBetweenStartDateAndLastAssignedOperation(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return Duration.between(
            getExpectedStartDateForUserTaskEvents(eventsForElement),
            getTimestampForLastZeebeEventsWithIntent(eventsForElement, ASSIGNED))
        .toMillis();
  }

  private long getDurationInMsBetweenAssignOperations(
      final List<ZeebeUserTaskRecordDto> eventsForElement,
      final String assigneeId1,
      final String assigneeId2) {
    return Duration.between(
            getTimestampForZeebeAssignEvents(eventsForElement, assigneeId1),
            getTimestampForZeebeAssignEvents(eventsForElement, assigneeId2))
        .toMillis();
  }

  private long getDurationInMsBetweenLastAssignOperationAndEnd(
      final List<ZeebeUserTaskRecordDto> eventsForElement, final String assigneeId) {
    return Duration.between(
            getTimestampForZeebeAssignEvents(eventsForElement, assigneeId),
            getExpectedEndDateForCompletedUserTaskEvents(eventsForElement))
        .toMillis();
  }

  private long getExpectedUserTaskInstanceIdFromRecords(
      final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return eventsForElement.stream()
        .findFirst()
        .map(ZeebeUserTaskRecordDto::getValue)
        .map(ZeebeUserTaskDataDto::getUserTaskKey)
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

  private String getExpectedIdFromAssignRecordsWithAssigneeId(
      final List<ZeebeUserTaskRecordDto> eventsForElement, final String assigneeId) {
    return eventsForElement.stream()
        .filter(
            event ->
                ASSIGNED.equals(event.getIntent())
                    && assigneeId.equals(event.getValue().getAssignee()))
        .findFirst()
        .map(ZeebeUserTaskRecordDto::getKey)
        .map(String::valueOf)
        .orElseThrow(eventNotFoundExceptionSupplier);
  }

  private void removeAllZeebeExportRecordsExceptUserTaskRecords() {
    databaseIntegrationTestExtension.deleteAllOtherZeebeRecordsWithPrefix(
        zeebeExtension.getZeebeRecordPrefix(), ZEEBE_USER_TASK_INDEX_NAME);
  }

  private List<ZeebeUserTaskRecordDto> getZeebeExportedUserTaskEvents() {
    return getZeebeExportedUserTaskEventsByElementId().get(USER_TASK);
  }

  private AssigneeOperationDto createAssigneeOperationDto(
      final String id,
      final UserTaskIdentityOperationType userTaskIdentityOperationType,
      final String userId,
      final OffsetDateTime timestamp) {
    final AssigneeOperationDto assigneeOperationDto = new AssigneeOperationDto();
    assigneeOperationDto.setId(id);
    assigneeOperationDto.setOperationType(userTaskIdentityOperationType.toString());
    assigneeOperationDto.setUserId(userId);
    assigneeOperationDto.setTimestamp(timestamp);
    return assigneeOperationDto;
  }
}
