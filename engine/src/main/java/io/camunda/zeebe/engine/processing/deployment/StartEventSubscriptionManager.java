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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.function.Predicate;

public class StartEventSubscriptionManager {

  private final MessageStartEventSubscriptionRecord messageSubscriptionRecord =
      new MessageStartEventSubscriptionRecord();
  private final SignalSubscriptionRecord signalSubscriptionRecord = new SignalSubscriptionRecord();

  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final SignalSubscriptionState signalSubscriptionState;
  private final KeyGenerator keyGenerator;

  public StartEventSubscriptionManager(
      final ProcessingState processingState, final KeyGenerator keyGenerator) {
    processState = processingState.getProcessState();
    messageStartEventSubscriptionState = processingState.getMessageStartEventSubscriptionState();
    signalSubscriptionState = processingState.getSignalSubscriptionState();
    this.keyGenerator = keyGenerator;
  }

  public void tryReOpenStartEventSubscription(
      final DeploymentRecord deploymentRecord, final StateWriter stateWriter) {

    for (final ProcessMetadata processRecord : deploymentRecord.processesMetadata()) {
      if (isLatestProcess(processRecord)) {
        closeExistingStartEventSubscriptions(processRecord, stateWriter);
        openStartEventSubscriptions(processRecord, stateWriter);
      }
    }
  }

  private boolean isLatestProcess(final ProcessMetadata processRecord) {
    return processState
            .getLatestProcessVersionByProcessId(processRecord.getBpmnProcessIdBuffer())
            .getVersion()
        == processRecord.getVersion();
  }

  private void closeExistingStartEventSubscriptions(
      final ProcessMetadata processRecord, final StateWriter stateWriter) {
    closeMessageExistingStartEventSubscriptions(processRecord, stateWriter);
    closeSignalExistingStartEventSubscriptions(processRecord, stateWriter);
  }

  private void closeMessageExistingStartEventSubscriptions(
      final ProcessMetadata processRecord, final StateWriter stateWriter) {
    final DeployedProcess lastMsgProcess =
        findLastStartProcess(processRecord, ExecutableCatchEventElement::isMessage);
    if (lastMsgProcess == null) {
      return;
    }

    messageStartEventSubscriptionState.visitSubscriptionsByProcessDefinition(
        lastMsgProcess.getKey(),
        subscription ->
            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageStartEventSubscriptionIntent.DELETED,
                subscription.getRecord()));
  }

  private void closeSignalExistingStartEventSubscriptions(
      final ProcessMetadata processRecord, final StateWriter stateWriter) {
    final DeployedProcess lastSignalProcess =
        findLastStartProcess(processRecord, ExecutableCatchEventElement::isSignal);
    if (lastSignalProcess == null) {
      return;
    }

    signalSubscriptionState.visitStartEventSubscriptionsByProcessDefinitionKey(
        lastSignalProcess.getKey(),
        subscription ->
            stateWriter.appendFollowUpEvent(
                subscription.getKey(), SignalSubscriptionIntent.DELETED, subscription.getRecord()));
  }

  private DeployedProcess findLastStartProcess(
      final ProcessMetadata processRecord,
      final Predicate<ExecutableCatchEventElement> hasStartEventMatching) {
    for (int version = processRecord.getVersion() - 1; version > 0; --version) {
      final DeployedProcess lastStartProcess =
          processState.getProcessByProcessIdAndVersion(
              processRecord.getBpmnProcessIdBuffer(), version);
      if (lastStartProcess != null
          && lastStartProcess.getProcess().getStartEvents().stream()
              .anyMatch(hasStartEventMatching)) {
        return lastStartProcess;
      }
    }

    return null;
  }

  private void openStartEventSubscriptions(
      final ProcessMetadata processRecord, final StateWriter stateWriter) {
    final long processDefinitionKey = processRecord.getKey();
    final DeployedProcess processDefinition = processState.getProcessByKey(processDefinitionKey);
    final ExecutableProcess process = processDefinition.getProcess();
    final List<ExecutableStartEvent> startEvents = process.getStartEvents();
    for (final ExecutableStartEvent startEvent : startEvents) {
      if (startEvent.isMessage()) {
        openMessageStartEventSubscription(
            processDefinition, processDefinitionKey, startEvent, stateWriter);
      } else if (startEvent.isSignal()) {
        openSignalStartEventSubscription(
            processDefinition, processDefinitionKey, startEvent, stateWriter);
      }
    }
  }

  private void openMessageStartEventSubscription(
      final DeployedProcess processDefinition,
      final long processDefinitionKey,
      final ExecutableStartEvent startEvent,
      final StateWriter stateWriter) {
    final ExecutableMessage message = startEvent.getMessage();

    message
        .getMessageName()
        .map(BufferUtil::wrapString)
        .ifPresent(
            messageNameBuffer -> {
              messageSubscriptionRecord.reset();
              messageSubscriptionRecord
                  .setMessageName(messageNameBuffer)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(processDefinition.getBpmnProcessId())
                  .setStartEventId(startEvent.getId());

              final var subscriptionKey = keyGenerator.nextKey();
              stateWriter.appendFollowUpEvent(
                  subscriptionKey,
                  MessageStartEventSubscriptionIntent.CREATED,
                  messageSubscriptionRecord);
            });
  }

  private void openSignalStartEventSubscription(
      final DeployedProcess processDefinition,
      final long processDefinitionKey,
      final ExecutableStartEvent startEvent,
      final StateWriter stateWriter) {
    final ExecutableSignal signal = startEvent.getSignal();

    signal
        .getSignalName()
        .map(BufferUtil::wrapString)
        .ifPresent(
            signalNameBuffer -> {
              signalSubscriptionRecord.reset();
              signalSubscriptionRecord
                  .setSignalName(signalNameBuffer)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(processDefinition.getBpmnProcessId())
                  .setCatchEventId(startEvent.getId());

              final var subscriptionKey = keyGenerator.nextKey();
              stateWriter.appendFollowUpEvent(
                  subscriptionKey, SignalSubscriptionIntent.CREATED, signalSubscriptionRecord);
            });
  }
}
