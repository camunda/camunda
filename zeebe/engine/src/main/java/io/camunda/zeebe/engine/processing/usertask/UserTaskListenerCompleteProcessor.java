/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask.TaskListenerEventType;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.Optional;

public final class UserTaskListenerCompleteProcessor
    implements TypedRecordProcessor<UserTaskRecord> {

  private static final String DEFAULT_ACTION = "complete";

  private final ElementInstanceState elementInstanceState;
  private final EventHandle eventHandle;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final ProcessState processState;
  private final BpmnJobBehavior jobBehavior;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskListenerCompleteProcessor(
      final ProcessingState state,
      final EventHandle eventHandle,
      final Writers writers,
      final BpmnJobBehavior bpmnJobBehavior) {
    elementInstanceState = state.getElementInstanceState();
    this.eventHandle = eventHandle;
    stateWriter = writers.state();
    commandWriter = writers.command();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    processState = state.getProcessState();
    jobBehavior = bpmnJobBehavior;
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.COMPLETING), "complete", state.getUserTaskState());
  }

  private void onTaskListenerComplete(
      final TypedRecord<UserTaskRecord> record, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = record.getKey();

    userTaskRecord.setVariables(record.getValue().getVariablesBuffer());
    userTaskRecord.setAction(record.getValue().getActionOrDefault(DEFAULT_ACTION));

    final ExecutableUserTask element =
        processState.getFlowElement(
            userTaskRecord.getProcessDefinitionKey(),
            userTaskRecord.getTenantId(),
            userTaskRecord.getElementIdBuffer(),
            ExecutableUserTask.class);

    final var userTaskElementInstanceKey = record.getValue().getElementInstanceKey();
    final ElementInstance userTaskElementInstance =
        elementInstanceState.getInstance(userTaskElementInstanceKey);

    final Integer taskListenerIndex =
        userTaskElementInstance.getTaskListenerIndex(TaskListenerEventType.COMPLETE);
    final List<String> taskListeners = element.getTaskListeners(TaskListenerEventType.COMPLETE);
    findNextTaskListener(taskListeners, taskListenerIndex)
        .ifPresentOrElse(
            listenerType ->
                jobBehavior.createNewTaskListenerJob(
                    listenerType, JobListenerEventType.COMPLETE, userTaskRecord, element),
            () -> {
              stateWriter.appendFollowUpEvent(
                  userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord);
              completeElementInstance(userTaskRecord);
              responseWriter.writeEventOnCommand(
                  userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord, record);
            });
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> userTaskRecord) {
    preconditionChecker
        .check(userTaskRecord)
        .ifRightOrLeft(
            persistedRecord -> onTaskListenerComplete(userTaskRecord, persistedRecord),
            violation -> {
              rejectionWriter.appendRejection(
                  userTaskRecord, violation.getLeft(), violation.getRight());
              responseWriter.writeRejectionOnCommand(
                  userTaskRecord, violation.getLeft(), violation.getRight());
            });
  }

  private Optional<String> findNextTaskListener(
      final List<String> listeners, final int nextListenerIndex) {
    return listeners.stream().skip(nextListenerIndex).findFirst();
  }

  private void completeElementInstance(final UserTaskRecord userTaskRecord) {
    final var userTaskElementInstanceKey = userTaskRecord.getElementInstanceKey();

    final ElementInstance userTaskElementInstance =
        elementInstanceState.getInstance(userTaskElementInstanceKey);

    if (userTaskElementInstance != null) {
      final long scopeKey = userTaskElementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        eventHandle.triggeringProcessEvent(userTaskRecord);
        commandWriter.appendFollowUpCommand(
            userTaskElementInstanceKey,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            userTaskElementInstance.getValue());
      }
    }
  }
}
