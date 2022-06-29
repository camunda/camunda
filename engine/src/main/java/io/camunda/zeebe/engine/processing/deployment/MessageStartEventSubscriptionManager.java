/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public class MessageStartEventSubscriptionManager {

  private final MessageStartEventSubscriptionRecord subscriptionRecord =
      new MessageStartEventSubscriptionRecord();

  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final KeyGenerator keyGenerator;

  public MessageStartEventSubscriptionManager(
      final ProcessState processState,
      final MessageStartEventSubscriptionState messageStartEventSubscriptionState,
      final KeyGenerator keyGenerator) {
    this.processState = processState;
    this.messageStartEventSubscriptionState = messageStartEventSubscriptionState;
    this.keyGenerator = keyGenerator;
  }

  public void tryReOpenMessageStartEventSubscription(
      final DeploymentRecord deploymentRecord, final StateBuilder stateBuilder) {

    for (final ProcessMetadata processRecord : deploymentRecord.processesMetadata()) {
      if (isLatestProcess(processRecord)) {
        closeExistingMessageStartEventSubscriptions(processRecord, stateBuilder);
        openMessageStartEventSubscriptions(processRecord, stateBuilder);
      }
    }
  }

  private boolean isLatestProcess(final ProcessMetadata processRecord) {
    return processState
            .getLatestProcessVersionByProcessId(processRecord.getBpmnProcessIdBuffer())
            .getVersion()
        == processRecord.getVersion();
  }

  private void closeExistingMessageStartEventSubscriptions(
      final ProcessMetadata processRecord, final StateBuilder stateBuilder) {
    final DeployedProcess lastMsgProcess = findLastMessageStartProcess(processRecord);
    if (lastMsgProcess == null) {
      return;
    }

    messageStartEventSubscriptionState.visitSubscriptionsByProcessDefinition(
        lastMsgProcess.getKey(),
        subscription ->
            stateBuilder.appendFollowUpEvent(
                subscription.getKey(),
                MessageStartEventSubscriptionIntent.DELETED,
                subscription.getRecord()));
  }

  private DeployedProcess findLastMessageStartProcess(final ProcessMetadata processRecord) {
    for (int version = processRecord.getVersion() - 1; version > 0; --version) {
      final DeployedProcess lastMsgProcess =
          processState.getProcessByProcessIdAndVersion(
              processRecord.getBpmnProcessIdBuffer(), version);
      if (lastMsgProcess != null
          && lastMsgProcess.getProcess().getStartEvents().stream()
              .anyMatch(ExecutableCatchEventElement::isMessage)) {
        return lastMsgProcess;
      }
    }

    return null;
  }

  private void openMessageStartEventSubscriptions(
      final ProcessMetadata processRecord, final StateBuilder stateBuilder) {
    final long processDefinitionKey = processRecord.getKey();
    final DeployedProcess processDefinition = processState.getProcessByKey(processDefinitionKey);
    final ExecutableProcess process = processDefinition.getProcess();
    final List<ExecutableStartEvent> startEvents = process.getStartEvents();

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
                      .setProcessDefinitionKey(processDefinitionKey)
                      .setBpmnProcessId(process.getId())
                      .setStartEventId(startEvent.getId());

                  final var subscriptionKey = keyGenerator.nextKey();
                  stateBuilder.appendFollowUpEvent(
                      subscriptionKey,
                      MessageStartEventSubscriptionIntent.CREATED,
                      subscriptionRecord);
                });
      }
    }
  }
}
