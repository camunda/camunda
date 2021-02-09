/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.WorkflowRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class DeploymentCreatedProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final MutableWorkflowState workflowState;
  private final boolean isDeploymentPartition;
  private final MessageStartEventSubscriptionRecord subscriptionRecord =
      new MessageStartEventSubscriptionRecord();

  public DeploymentCreatedProcessor(
      final MutableWorkflowState workflowState, final boolean isDeploymentPartition) {
    this.workflowState = workflowState;
    this.isDeploymentPartition = isDeploymentPartition;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final DeploymentRecord deploymentEvent = event.getValue();

    if (isDeploymentPartition) {
      streamWriter.appendFollowUpCommand(
          event.getKey(), DeploymentIntent.DISTRIBUTE, deploymentEvent);
    }

    for (final WorkflowRecord workflowRecord : deploymentEvent.workflows()) {
      if (isLatestWorkflow(workflowRecord)) {
        closeExistingMessageStartEventSubscriptions(workflowRecord, streamWriter);
        openMessageStartEventSubscriptions(workflowRecord, streamWriter);
      }
    }
  }

  private boolean isLatestWorkflow(final WorkflowRecord workflowRecord) {
    return workflowState
            .getLatestWorkflowVersionByProcessId(workflowRecord.getBpmnProcessIdBuffer())
            .getVersion()
        == workflowRecord.getVersion();
  }

  private void closeExistingMessageStartEventSubscriptions(
      final WorkflowRecord workflowRecord, final TypedStreamWriter streamWriter) {
    final DeployedWorkflow lastMsgWorkflow = findLastMessageStartWorkflow(workflowRecord);
    if (lastMsgWorkflow == null) {
      return;
    }

    subscriptionRecord.reset();
    subscriptionRecord.setWorkflowKey(lastMsgWorkflow.getKey());
    streamWriter.appendNewCommand(MessageStartEventSubscriptionIntent.CLOSE, subscriptionRecord);
  }

  private DeployedWorkflow findLastMessageStartWorkflow(final WorkflowRecord workflowRecord) {
    for (int version = workflowRecord.getVersion() - 1; version > 0; --version) {
      final DeployedWorkflow lastMsgWorkflow =
          workflowState.getWorkflowByProcessIdAndVersion(
              workflowRecord.getBpmnProcessIdBuffer(), version);
      if (lastMsgWorkflow != null
          && lastMsgWorkflow.getWorkflow().getStartEvents().stream().anyMatch(e -> e.isMessage())) {
        return lastMsgWorkflow;
      }
    }

    return null;
  }

  private void openMessageStartEventSubscriptions(
      final WorkflowRecord workflowRecord, final TypedStreamWriter streamWriter) {
    final long workflowKey = workflowRecord.getKey();
    final DeployedWorkflow workflowDefinition = workflowState.getWorkflowByKey(workflowKey);
    final ExecutableWorkflow workflow = workflowDefinition.getWorkflow();
    final List<ExecutableStartEvent> startEvents = workflow.getStartEvents();

    // if startEvents contain message events
    for (final ExecutableCatchEventElement startEvent : startEvents) {
      if (startEvent.isMessage()) {
        final ExecutableMessage message = startEvent.getMessage();

        message
            .getMessageName()
            .map(BufferUtil::wrapString)
            .ifPresent(
                messageNameBuffer -> {
                  subscriptionRecord.reset();
                  subscriptionRecord
                      .setMessageName(messageNameBuffer)
                      .setWorkflowKey(workflowKey)
                      .setBpmnProcessId(workflow.getId())
                      .setStartEventId(startEvent.getId());
                  streamWriter.appendNewCommand(
                      MessageStartEventSubscriptionIntent.OPEN, subscriptionRecord);
                });
      }
    }
  }
}
