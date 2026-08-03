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
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
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
  public void importCompletedUnclaimedZeebeUserTaskData_viaWriterAndViaUpdateScript() {
    // Covers both import code paths for a completed (unclaimed) user task: scenario 1 imports
    // creation and completion together in one batch (upsert via the writer); scenario 2 imports
    // creation and completion as two separate batches (upsert via the update script).

    // given (writer path)
    final ProcessInstanceEvent instanceA =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    List<ZeebeUserTaskRecordDto> userTaskEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    zeebeExtension.completeZeebeUserTask(getExpectedUserTaskInstanceIdFromRecords(userTaskEventsA));
    waitUntilUserTaskRecordWithIntentExported(COMPLETED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (writer path)
    importAllZeebeEntitiesFromScratch();

    // then (writer path)
    userTaskEventsA = getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    final OffsetDateTime expectedEndDateA =
        getExpectedEndDateForCompletedUserTaskEvents(userTaskEventsA);
    final FlowNodeInstanceDto expectedUserTaskA =
        createRunningUserTaskInstance(instanceA, userTaskEventsA);
    expectedUserTaskA.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTaskA.setEndDate(expectedEndDateA);
    expectedUserTaskA.setIdleDurationInMs(0L);
    expectedUserTaskA.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEventsA));
    expectedUserTaskA.setWorkDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEventsA));

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instanceA.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instanceA.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instanceA.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only the userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTaskA);
            });

    // given (update-script path)
    final ProcessInstanceEvent instanceB =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordExportedForInstance(instanceB.getProcessInstanceKey());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    final List<ZeebeUserTaskRecordDto> runningUserTaskEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskB =
        createRunningUserTaskInstance(instanceB, runningUserTaskEventsB);
    List<ZeebeUserTaskRecordDto> userTaskEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    zeebeExtension.completeZeebeUserTask(getExpectedUserTaskInstanceIdFromRecords(userTaskEventsB));
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        COMPLETED, instanceB.getProcessInstanceKey());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (update-script path)
    importAllZeebeEntitiesFromLastIndex();

    // then (update-script path)
    userTaskEventsB = getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final OffsetDateTime expectedEndDateB =
        getExpectedEndDateForCompletedUserTaskEvents(userTaskEventsB);
    expectedUserTaskB.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTaskB.setEndDate(expectedEndDateB);
    expectedUserTaskB.setIdleDurationInMs(0L);
    expectedUserTaskB.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEventsB));
    expectedUserTaskB.setWorkDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(userTaskEventsB));

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).hasSize(2);
    final ProcessInstanceDto savedInstanceB =
        getProcessInstanceForId(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessInstanceId())
        .isEqualTo(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessDefinitionId())
        .isEqualTo(String.valueOf(instanceB.getProcessDefinitionKey()));
    assertThat(savedInstanceB.getProcessDefinitionKey()).isEqualTo(instanceB.getBpmnProcessId());
    assertThat(savedInstanceB.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
    assertThat(savedInstanceB.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(savedInstanceB.getFlowNodeInstances())
        .singleElement() // only the userTask was imported because all other records were removed
        .usingRecursiveComparison()
        .isEqualTo(expectedUserTaskB);
  }

  @Test
  public void importCanceledUnclaimedZeebeUserTaskData_viaWriterAndViaUpdateScript() {
    // Covers both import code paths for a canceled (unclaimed) user task: scenario 1 imports
    // creation and cancellation together in one batch (upsert via the writer); scenario 2 imports
    // creation and cancellation as two separate batches (upsert via the update script).

    // given (writer path)
    final ProcessInstanceEvent instanceA =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    zeebeExtension.cancelProcessInstance(instanceA.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExported(UserTaskIntent.CANCELED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (writer path)
    importAllZeebeEntitiesFromScratch();

    // then (writer path) the import in one batch correctly set all fields in the new instance
    // document
    final List<ZeebeUserTaskRecordDto> exportedEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskA =
        createRunningUserTaskInstance(instanceA, exportedEventsA);
    final OffsetDateTime expectedEndDateA =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEventsA);
    final Long expectedTotalAndIdleDurationA =
        Duration.between(expectedUserTaskA.getStartDate(), expectedEndDateA).toMillis();
    expectedUserTaskA.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTaskA.setEndDate(expectedEndDateA);
    expectedUserTaskA.setTotalDurationInMs(expectedTotalAndIdleDurationA);
    expectedUserTaskA.setIdleDurationInMs(expectedTotalAndIdleDurationA);
    expectedUserTaskA.setWorkDurationInMs(0L);
    expectedUserTaskA.setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instanceA.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instanceA.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instanceA.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  .singleElement() // only userTask was imported because all other records were
                  // removed
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTaskA);
            });

    // given (update-script path)
    final ProcessInstanceEvent instanceB =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordExportedForInstance(instanceB.getProcessInstanceKey());
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    zeebeExtension.cancelProcessInstance(instanceB.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        UserTaskIntent.CANCELED, instanceB.getProcessInstanceKey());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (update-script path)
    importAllZeebeEntitiesFromLastIndex();

    // then (update-script path) the import over two batches correctly updates all fields with the
    // update script
    final List<ZeebeUserTaskRecordDto> exportedEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskB =
        createRunningUserTaskInstance(instanceB, exportedEventsB);
    final OffsetDateTime expectedEndDateB =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEventsB);
    expectedUserTaskB.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTaskB.setEndDate(expectedEndDateB);
    expectedUserTaskB.setTotalDurationInMs(
        Duration.between(expectedUserTaskB.getStartDate(), expectedEndDateB).toMillis());
    expectedUserTaskB.setIdleDurationInMs(
        Duration.between(expectedUserTaskB.getStartDate(), expectedEndDateB).toMillis());
    expectedUserTaskB.setWorkDurationInMs(0L);
    expectedUserTaskB.setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).hasSize(2);
    final ProcessInstanceDto savedInstanceB =
        getProcessInstanceForId(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessInstanceId())
        .isEqualTo(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessDefinitionId())
        .isEqualTo(String.valueOf(instanceB.getProcessDefinitionKey()));
    assertThat(savedInstanceB.getProcessDefinitionKey()).isEqualTo(instanceB.getBpmnProcessId());
    assertThat(savedInstanceB.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
    assertThat(savedInstanceB.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(savedInstanceB.getFlowNodeInstances())
        // only userTask was imported because all other records were removed
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(expectedUserTaskB);
  }

  @Test
  public void importCanceledClaimedZeebeUserTaskData_viaWriterAndViaUpdateScript() {
    // Covers both import code paths for a canceled (claimed) user task: scenario 1 imports
    // creation, claim, and cancellation together in one batch (upsert via the writer); scenario 2
    // imports them as two separate batches (upsert via the update script).

    // given (writer path)
    final ProcessInstanceEvent instanceA =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    List<ZeebeUserTaskRecordDto> exportedEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEventsA), ASSIGNEE_ID);
    zeebeExtension.cancelProcessInstance(instanceA.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExported(CANCELED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (writer path)
    importAllZeebeEntitiesFromScratch();

    // then (writer path)
    exportedEventsA = getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskA =
        createRunningUserTaskInstance(instanceA, exportedEventsA);
    final OffsetDateTime expectedEndDateA =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEventsA);
    final OffsetDateTime assignDateA = getTimestampForAssignedUserTaskEvents(exportedEventsA);
    expectedUserTaskA.setAssignee(ASSIGNEE_ID);
    expectedUserTaskA.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTaskA.setEndDate(expectedEndDateA);
    expectedUserTaskA.setTotalDurationInMs(
        Duration.between(expectedUserTaskA.getStartDate(), expectedEndDateA).toMillis());
    expectedUserTaskA.setIdleDurationInMs(
        Duration.between(expectedUserTaskA.getStartDate(), assignDateA).toMillis());
    expectedUserTaskA.setWorkDurationInMs(
        Duration.between(assignDateA, expectedEndDateA).toMillis());
    expectedUserTaskA.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEventsA, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getTimestampForAssignedUserTaskEvents(exportedEventsA))));
    expectedUserTaskA.setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instanceA.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instanceA.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instanceA.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTaskA);
            });

    // given (update-script path)
    final ProcessInstanceEvent instanceB =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, DUE_DATE));
    waitUntilUserTaskRecordExportedForInstance(instanceB.getProcessInstanceKey());
    List<ZeebeUserTaskRecordDto> exportedEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEventsB), ASSIGNEE_ID);
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        ASSIGNED, instanceB.getProcessInstanceKey());

    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    zeebeExtension.cancelProcessInstance(instanceB.getProcessInstanceKey());
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        UserTaskIntent.CANCELED, instanceB.getProcessInstanceKey());
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (update-script path)
    importAllZeebeEntitiesFromLastIndex();

    // then (update-script path)
    exportedEventsB = getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final OffsetDateTime expectedEndDateB =
        getExpectedEndDateForCanceledUserTaskEvents(exportedEventsB);
    final OffsetDateTime assignDateB = getTimestampForAssignedUserTaskEvents(exportedEventsB);
    final FlowNodeInstanceDto expectedUserTaskB =
        createRunningUserTaskInstance(instanceB, exportedEventsB);
    expectedUserTaskB.setAssignee(ASSIGNEE_ID);
    expectedUserTaskB.setDueDate(EXPECTED_DUE_DATE);
    expectedUserTaskB.setEndDate(expectedEndDateB);
    expectedUserTaskB.setTotalDurationInMs(
        Duration.between(expectedUserTaskB.getStartDate(), expectedEndDateB).toMillis());
    expectedUserTaskB.setIdleDurationInMs(
        Duration.between(expectedUserTaskB.getStartDate(), assignDateB).toMillis());
    expectedUserTaskB.setWorkDurationInMs(
        Duration.between(assignDateB, expectedEndDateB).toMillis());
    expectedUserTaskB.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEventsB, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getTimestampForAssignedUserTaskEvents(exportedEventsB))));
    expectedUserTaskB.setCanceled(true);

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).hasSize(2);
    final ProcessInstanceDto savedInstanceB =
        getProcessInstanceForId(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessInstanceId())
        .isEqualTo(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessDefinitionId())
        .isEqualTo(String.valueOf(instanceB.getProcessDefinitionKey()));
    assertThat(savedInstanceB.getProcessDefinitionKey()).isEqualTo(instanceB.getBpmnProcessId());
    assertThat(savedInstanceB.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
    assertThat(savedInstanceB.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(savedInstanceB.getFlowNodeInstances())
        // only userTask was imported because all other records were removed
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(expectedUserTaskB);
  }

  @Test
  public void importClaimOperation_viaWriterAndViaUpdateScript() {
    // Covers both import code paths for a claim operation: scenario 1 imports creation and claim
    // together in one batch (upsert via the writer); scenario 2 imports creation and claim as two
    // separate batches (upsert via the update script).

    // given (writer path)
    final ProcessInstanceEvent instanceA =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    List<ZeebeUserTaskRecordDto> exportedEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEventsA), ASSIGNEE_ID);
    waitUntilUserTaskRecordWithIntentExported(ASSIGNED);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (writer path)
    importAllZeebeEntitiesFromScratch();

    // then (writer path)
    exportedEventsA = getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskA =
        createRunningUserTaskInstance(instanceA, exportedEventsA);
    expectedUserTaskA.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEventsA));
    expectedUserTaskA.setAssignee(ASSIGNEE_ID);
    expectedUserTaskA.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEventsA, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getTimestampForZeebeAssignEvents(exportedEventsA, ASSIGNEE_ID))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instanceA.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instanceA.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instanceA.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(expectedUserTaskA);
            });

    // given (update-script path)
    final ProcessInstanceEvent instanceB =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordExportedForInstance(instanceB.getProcessInstanceKey());
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    List<ZeebeUserTaskRecordDto> exportedEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    zeebeExtension.assignUserTask(
        getExpectedUserTaskInstanceIdFromRecords(exportedEventsB), ASSIGNEE_ID);
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        ASSIGNED, instanceB.getProcessInstanceKey());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (update-script path)
    importAllZeebeEntitiesFromLastIndex();

    // then (update-script path)
    exportedEventsB = getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskB =
        createRunningUserTaskInstance(instanceB, exportedEventsB);
    expectedUserTaskB.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEventsB));
    expectedUserTaskB.setAssignee(ASSIGNEE_ID);
    expectedUserTaskB.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEventsB, ASSIGNED),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getTimestampForZeebeAssignEvents(exportedEventsB, ASSIGNEE_ID))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).hasSize(2);
    final ProcessInstanceDto savedInstanceB =
        getProcessInstanceForId(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessInstanceId())
        .isEqualTo(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessDefinitionId())
        .isEqualTo(String.valueOf(instanceB.getProcessDefinitionKey()));
    assertThat(savedInstanceB.getProcessDefinitionKey()).isEqualTo(instanceB.getBpmnProcessId());
    assertThat(savedInstanceB.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
    assertThat(savedInstanceB.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(savedInstanceB.getFlowNodeInstances())
        // only userTask was imported because all other records were removed
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(expectedUserTaskB);
  }

  @Test
  public void importUnclaimOperation_viaWriterAndViaUpdateScript() {
    // Covers both import code paths for an unclaim operation: scenario 1 imports creation and
    // unclaim together in one batch (upsert via the writer); scenario 2 imports them as two
    // separate batches (upsert via the update script).

    // given (writer path)
    final ProcessInstanceEvent instanceA =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, null, ASSIGNEE_ID));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);

    if (isZeebeVersion87_OrLater()) {
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

    // when (writer path)
    importAllZeebeEntitiesFromScratch();

    // then (writer path)
    final List<ZeebeUserTaskRecordDto> exportedEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(instanceA.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(instanceA.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(instanceA.getBpmnProcessId());
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
              final FlowNodeInstanceDto runningUserTaskInstanceA =
                  createRunningUserTaskInstance(instanceA, exportedEventsA);
              runningUserTaskInstanceA.setIdleDurationInMs(0L);
              runningUserTaskInstanceA.setWorkDurationInMs(
                  isZeebeVersion87_OrLater()
                      ? getDurationInMsBetweenStartDateAndLastAssignedOperation(exportedEventsA)
                      : getDurationInMsBetweenStartAndFirstAssignOperation(exportedEventsA));
              runningUserTaskInstanceA.setAssigneeOperations(
                  List.of(
                      createAssigneeOperationDto(
                          getExpectedIdFromRecords(exportedEventsA, CREATING),
                          CLAIM_OPERATION_TYPE,
                          ASSIGNEE_ID,
                          getExpectedStartDateForUserTaskEvents(exportedEventsA)),
                      createAssigneeOperationDto(
                          getExpectedIdFromRecords(exportedEventsA, ASSIGNED),
                          UNCLAIM_OPERATION_TYPE,
                          null,
                          getTimestampForLastZeebeEventsWithIntent(exportedEventsA, ASSIGNED))));
              assertThat(savedInstance.getFlowNodeInstances())
                  // only userTask was imported because all other records were removed
                  .singleElement()
                  .usingRecursiveComparison()
                  .isEqualTo(runningUserTaskInstanceA);
            });

    // given (update-script path)
    final ProcessInstanceEvent instanceB =
        deployAndStartInstanceForProcess(
            createSimpleNativeUserTaskProcessWithAssignee(TEST_PROCESS, null, ASSIGNEE_ID));
    waitUntilUserTaskRecordExportedForInstance(instanceB.getProcessInstanceKey());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    List<ZeebeUserTaskRecordDto> exportedEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());

    if (isZeebeVersion87_OrLater()) {
      // to wait for `ASSIGNED` event triggered by Zeebe after UT creation with the defined
      // `assignee`
      waitUntilUserTaskRecordWithIntentExportedForInstance(
          1, ASSIGNED, instanceB.getProcessInstanceKey());
      zeebeExtension.unassignUserTask(
          getExpectedUserTaskInstanceIdFromRecords(
              getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey())));
      // wait for the 2nd `ASSIGNED` event triggered by UT unassign operation
      waitUntilUserTaskRecordWithIntentExportedForInstance(
          2, ASSIGNED, instanceB.getProcessInstanceKey());
    } else {
      zeebeExtension.unassignUserTask(getExpectedUserTaskInstanceIdFromRecords(exportedEventsB));
      waitUntilUserTaskRecordWithIntentExportedForInstance(
          ASSIGNED, instanceB.getProcessInstanceKey());
    }
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (update-script path)
    importAllZeebeEntitiesFromLastIndex();

    // then (update-script path)
    exportedEventsB = getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final FlowNodeInstanceDto expectedUserTaskB =
        createRunningUserTaskInstance(instanceB, exportedEventsB);
    expectedUserTaskB.setIdleDurationInMs(0L);
    expectedUserTaskB.setWorkDurationInMs(
        isZeebeVersion87_OrLater()
            ? getDurationInMsBetweenStartDateAndLastAssignedOperation(exportedEventsB)
            : getDurationInMsBetweenStartAndFirstAssignOperation(exportedEventsB));
    expectedUserTaskB.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEventsB, CREATING),
                CLAIM_OPERATION_TYPE,
                ASSIGNEE_ID,
                getExpectedStartDateForUserTaskEvents(exportedEventsB)),
            createAssigneeOperationDto(
                getExpectedIdFromRecords(exportedEventsB, ASSIGNED),
                UNCLAIM_OPERATION_TYPE,
                null,
                isZeebeVersion87_OrLater()
                    ? getTimestampForZeebeLastAssignedEvents(exportedEventsB, "")
                    : getTimestampForZeebeUnassignEvent(exportedEventsB))));

    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).hasSize(2);
    final ProcessInstanceDto savedInstanceB =
        getProcessInstanceForId(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessInstanceId())
        .isEqualTo(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getProcessDefinitionId())
        .isEqualTo(String.valueOf(instanceB.getProcessDefinitionKey()));
    assertThat(savedInstanceB.getProcessDefinitionKey()).isEqualTo(instanceB.getBpmnProcessId());
    assertThat(savedInstanceB.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
    assertThat(savedInstanceB.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
    assertThat(savedInstanceB.getFlowNodeInstances())
        // only userTask was imported because all other records were removed
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(expectedUserTaskB);
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
  public void importMultipleAssigneeOperations_viaWriterAndViaUpdateScript() {
    // Covers both import code paths for a sequence of claim/unclaim/claim operations: scenario 1
    // imports creation and all assignee operations together in one batch (upsert via the writer);
    // scenario 2 imports them as two separate batches (upsert via the update script).
    final String assigneeId1 = ASSIGNEE_ID + "1";
    final String assigneeId2 = ASSIGNEE_ID + "2";

    // given (writer path)
    final ProcessInstanceEvent instanceA =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    final List<ZeebeUserTaskRecordDto> userTaskEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    final long userTaskInstanceIdA = getExpectedUserTaskInstanceIdFromRecords(userTaskEventsA);
    zeebeExtension.assignUserTask(userTaskInstanceIdA, assigneeId1);
    zeebeExtension.unassignUserTask(userTaskInstanceIdA);
    zeebeExtension.assignUserTask(userTaskInstanceIdA, assigneeId2);
    zeebeExtension.completeZeebeUserTask(userTaskInstanceIdA);
    waitUntilUserTaskRecordWithIntentExported(COMPLETED);

    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (writer path)
    importAllZeebeEntitiesFromScratch();

    // then (writer path)
    final List<ZeebeUserTaskRecordDto> exportedEventsA =
        getZeebeExportedUserTaskEvents(instanceA.getProcessInstanceKey());
    final FlowNodeInstanceDto runningUserTaskInstanceA =
        createRunningUserTaskInstance(instanceA, exportedEventsA);
    runningUserTaskInstanceA.setEndDate(
        getExpectedEndDateForCompletedUserTaskEvents(exportedEventsA));
    runningUserTaskInstanceA.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEventsA)
            + getDurationInMsBetweenAssignOperations(exportedEventsA, "", assigneeId2));
    runningUserTaskInstanceA.setWorkDurationInMs(
        getDurationInMsBetweenAssignOperations(exportedEventsA, assigneeId1, "")
            + getDurationInMsBetweenLastAssignOperationAndEnd(exportedEventsA, assigneeId2));
    runningUserTaskInstanceA.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(exportedEventsA));
    runningUserTaskInstanceA.setAssignee(assigneeId2);
    runningUserTaskInstanceA.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEventsA, assigneeId1),
                CLAIM_OPERATION_TYPE,
                assigneeId1,
                getTimestampForZeebeAssignEvents(exportedEventsA, assigneeId1)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEventsA, ""),
                UNCLAIM_OPERATION_TYPE,
                null,
                getTimestampForZeebeUnassignEvent(exportedEventsA)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEventsA, assigneeId2),
                CLAIM_OPERATION_TYPE,
                assigneeId2,
                getTimestampForZeebeAssignEvents(exportedEventsA, assigneeId2))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getFlowNodeInstances())
                    .singleElement()
                    .usingRecursiveComparison()
                    .isEqualTo(runningUserTaskInstanceA));

    // given (update-script path)
    final ProcessInstanceEvent instanceB =
        deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(TEST_PROCESS, null));
    waitUntilUserTaskRecordExportedForInstance(instanceB.getProcessInstanceKey());
    final List<ZeebeUserTaskRecordDto> userTaskEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final long userTaskInstanceIdB = getExpectedUserTaskInstanceIdFromRecords(userTaskEventsB);
    zeebeExtension.assignUserTask(userTaskInstanceIdB, assigneeId1);
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        ASSIGNED, instanceB.getProcessInstanceKey());
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    zeebeExtension.unassignUserTask(userTaskInstanceIdB);
    zeebeExtension.assignUserTask(userTaskInstanceIdB, assigneeId2);
    zeebeExtension.completeZeebeUserTask(userTaskInstanceIdB);
    waitUntilUserTaskRecordWithIntentExportedForInstance(
        COMPLETED, instanceB.getProcessInstanceKey());

    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();

    // when (update-script path)
    importAllZeebeEntitiesFromLastIndex();

    // then (update-script path)
    final List<ZeebeUserTaskRecordDto> exportedEventsB =
        getZeebeExportedUserTaskEvents(instanceB.getProcessInstanceKey());
    final FlowNodeInstanceDto runningUserTaskInstanceB =
        createRunningUserTaskInstance(instanceB, exportedEventsB);
    runningUserTaskInstanceB.setEndDate(
        getExpectedEndDateForCompletedUserTaskEvents(exportedEventsB));
    runningUserTaskInstanceB.setIdleDurationInMs(
        getDurationInMsBetweenStartAndFirstAssignOperation(exportedEventsB)
            + getDurationInMsBetweenAssignOperations(exportedEventsB, "", assigneeId2));
    runningUserTaskInstanceB.setWorkDurationInMs(
        getDurationInMsBetweenAssignOperations(exportedEventsB, assigneeId1, "")
            + getDurationInMsBetweenLastAssignOperationAndEnd(exportedEventsB, assigneeId2));
    runningUserTaskInstanceB.setTotalDurationInMs(
        getExpectedTotalDurationForCompletedUserTask(exportedEventsB));
    runningUserTaskInstanceB.setAssignee(assigneeId2);
    runningUserTaskInstanceB.setAssigneeOperations(
        List.of(
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEventsB, assigneeId1),
                CLAIM_OPERATION_TYPE,
                assigneeId1,
                getTimestampForZeebeAssignEvents(exportedEventsB, assigneeId1)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEventsB, ""),
                UNCLAIM_OPERATION_TYPE,
                null,
                getTimestampForZeebeUnassignEvent(exportedEventsB)),
            createAssigneeOperationDto(
                getExpectedIdFromAssignRecordsWithAssigneeId(exportedEventsB, assigneeId2),
                CLAIM_OPERATION_TYPE,
                assigneeId2,
                getTimestampForZeebeAssignEvents(exportedEventsB, assigneeId2))));
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances()).hasSize(2);
    final ProcessInstanceDto savedInstanceB =
        getProcessInstanceForId(String.valueOf(instanceB.getProcessInstanceKey()));
    assertThat(savedInstanceB.getFlowNodeInstances())
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(runningUserTaskInstanceB);
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

  private List<ZeebeUserTaskRecordDto> getZeebeExportedUserTaskEvents() {
    return getZeebeExportedUserTaskEventsByElementId().get(USER_TASK);
  }

  /**
   * Scopes {@link #getZeebeExportedUserTaskEvents()} to a single process instance. Needed once a
   * test has more than one instance's user-task records sitting in the shared export index at the
   * same time (e.g. merged tests covering two import-path scenarios sequentially) — the unscoped
   * query groups records by elementId only, so records from multiple instances using the same
   * element id would otherwise be mixed together.
   */
  private List<ZeebeUserTaskRecordDto> getZeebeExportedUserTaskEvents(
      final long processInstanceKey) {
    return getZeebeExportedUserTaskEvents().stream()
        .filter(event -> event.getValue().getProcessInstanceKey() == processInstanceKey)
        .toList();
  }

  /**
   * Instance-scoped variants of {@code AbstractCCSMIT.waitUntilUserTaskRecordWithElementIdExported}
   * / {@code waitUntilUserTaskRecordWithIntentExported}. The inherited helpers only check "at least
   * N records with this elementId/intent exist" across the whole class-scoped export index — in a
   * merged test where an earlier scenario's instance already satisfies that count, the inherited
   * wait would return immediately without actually waiting for the current scenario's own record to
   * land, racing the assertions that follow.
   */
  private void waitUntilUserTaskRecordExportedForInstance(final long processInstanceKey) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.elementId,
        USER_TASK);
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.processInstanceKey,
        String.valueOf(processInstanceKey));
    waitUntilRecordMatchingQueryExported(DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME, query);
  }

  private void waitUntilUserTaskRecordWithIntentExportedForInstance(
      final UserTaskIntent intent, final long processInstanceKey) {
    waitUntilUserTaskRecordWithIntentExportedForInstance(1, intent, processInstanceKey);
  }

  private void waitUntilUserTaskRecordWithIntentExportedForInstance(
      final long minRecordCount, final UserTaskIntent intent, final long processInstanceKey) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.elementId,
        USER_TASK);
    query.addTermQuery(ZeebeRecordDto.Fields.intent, intent.name());
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.processInstanceKey,
        String.valueOf(processInstanceKey));
    waitUntilRecordMatchingQueryExported(
        minRecordCount, DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME, query);
  }

  private ProcessInstanceDto getProcessInstanceForId(final String processInstanceId) {
    return databaseIntegrationTestExtension.getAllProcessInstances().stream()
        .filter(instance -> instance.getProcessInstanceId().equals(processInstanceId))
        .findFirst()
        .orElseThrow(
            () ->
                new OptimizeIntegrationTestException(
                    "No process instance with id " + processInstanceId + " found"));
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
