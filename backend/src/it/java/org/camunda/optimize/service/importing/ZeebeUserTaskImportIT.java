/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import org.camunda.optimize.AbstractCCSMIT;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleNativeUserTaskProcess;

@DisabledIf("isZeebeVersionPre84")
public class ZeebeUserTaskImportIT extends AbstractCCSMIT {

  @Test
  public void importRunningZeebeUserTaskData() {
    // given
    final String processName = "someProcess";
    final String dueDate = "2023-11-01T12:00:00+01:00";
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(processName, dueDate));

    // when
    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();

    // then
    final Map<String, List<ZeebeUserTaskRecordDto>> exportedEvents = getZeebeExportedUserTaskEventsByElementId();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
        assertThat(savedInstance.getFlowNodeInstances())
          .singleElement() // only userTask was imported because all other records were removed
          .isEqualTo(
            createRunningUserTaskInstance(deployedInstance, exportedEvents, USER_TASK, dueDate)
          );
      });
  }

  @Test
  public void importCompletedZeebeUserTaskData() {
    // given
    final String processName = "someProcess";
    final String dueDate = "2023-11-01T12:00:00+01:00";
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleNativeUserTaskProcess(processName, dueDate));

    waitUntilUserTaskRecordWithElementIdExported(USER_TASK);
    // remove all zeebe records except userTask ones to test userTask import only
    removeAllZeebeExportRecordsExceptUserTaskRecords();
    importAllZeebeEntitiesFromScratch();
    final Map<String, List<ZeebeUserTaskRecordDto>> runningUserTaskEvents = getZeebeExportedUserTaskEventsByElementId();
    final FlowNodeInstanceDto expectedUserTask = createRunningUserTaskInstance(
      deployedInstance,
      runningUserTaskEvents,
      USER_TASK,
      dueDate
    );

    // when
    // fake userTask completion record. Once zeebeClient can complete userTasks, replace this with proper completion
    updateUserTaskRecordToSimulateCompletion();
    importAllZeebeEntitiesFromLastIndex();

    // then
    final Map<String, List<ZeebeUserTaskRecordDto>> completedUserTaskEvents = getZeebeExportedUserTaskEventsByElementId();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId()).isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId()).isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
        assertThat(savedInstance.getFlowNodeInstances())
          .singleElement() // only the userTask was imported because all other records were removed
          .isEqualTo(
            expectedUserTask
              .setEndDate(getExpectedEndDateForUserTaskEvents(completedUserTaskEvents.get(USER_TASK)))
              .setTotalDurationInMs(getExpectedDurationForEvents(
                expectedUserTask.getStartDate(),
                completedUserTaskEvents.get(USER_TASK)
              ))
          );
      });
  }

  private FlowNodeInstanceDto createRunningUserTaskInstance(final ProcessInstanceEvent deployedInstance,
                                                            final Map<String, List<ZeebeUserTaskRecordDto>> events,
                                                            final String eventId, final String expectedDueDate) {
    return new FlowNodeInstanceDto()
      .setFlowNodeInstanceId(String.valueOf(events.get(eventId).get(0).getValue().getElementInstanceKey()))
      .setFlowNodeId(eventId)
      .setFlowNodeType(FLOW_NODE_TYPE_USER_TASK)
      .setProcessInstanceId(String.valueOf(deployedInstance.getProcessInstanceKey()))
      .setDefinitionKey(String.valueOf(deployedInstance.getBpmnProcessId()))
      .setDefinitionVersion(String.valueOf(deployedInstance.getVersion()))
      .setTenantId(ZEEBE_DEFAULT_TENANT_ID)
      .setUserTaskInstanceId(getExpectedUserTaskInstanceIdForEvents(events.get(eventId)))
      .setStartDate(getExpectedStartDateForUserTaskEvents(events.get(eventId)))
      .setDueDate(OffsetDateTime.parse(expectedDueDate))
      .setCanceled(false);
  }

  private OffsetDateTime getExpectedStartDateForUserTaskEvents(final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForZeebeEventsWithIntent(eventsForElement, UserTaskIntent.CREATING);
  }

  private OffsetDateTime getExpectedEndDateForUserTaskEvents(final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return getTimestampForZeebeEventsWithIntent(eventsForElement, UserTaskIntent.COMPLETED);
  }

  private long getExpectedDurationForEvents(final OffsetDateTime startDate, final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return Duration.between(
      startDate,
      getExpectedEndDateForUserTaskEvents(eventsForElement)
    ).toMillis();
  }

  private String getExpectedUserTaskInstanceIdForEvents(final List<ZeebeUserTaskRecordDto> eventsForElement) {
    return eventsForElement.stream()
      .findFirst()
      .map(ZeebeUserTaskRecordDto::getValue)
      .map(ZeebeUserTaskDataDto::getUserTaskKey)
      .map(String::valueOf)
      .orElseThrow(eventNotFoundExceptionSupplier);
  }

  private void removeAllZeebeExportRecordsExceptUserTaskRecords() {
    databaseIntegrationTestExtension.deleteAllOtherZeebeRecordsWithPrefix(
      zeebeExtension.getZeebeRecordPrefix(),
      ZEEBE_USER_TASK_INDEX_NAME
    );
  }

  private void updateUserTaskRecordToSimulateCompletion() {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
      zeebeExtension.getZeebeRecordPrefix(),
      ZEEBE_USER_TASK_INDEX_NAME,
      "ctx._source.intent = \"COMPLETED\";\n" +
        "ctx._source.timestamp = ctx._source.timestamp + 1000;\n" + // also change timestamp to validate endDate and duration
        "ctx._source.sequence = ctx._source.sequence + 10;" // different sequence so event is imported
    );
  }
}
