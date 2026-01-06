/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableConditional;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class StartEventSubscriptionManager {

  private final MessageStartEventSubscriptionRecord messageSubscriptionRecord =
      new MessageStartEventSubscriptionRecord();
  private final SignalSubscriptionRecord signalSubscriptionRecord = new SignalSubscriptionRecord();
  private final ConditionalSubscriptionRecord conditionalSubscriptionRecord =
      new ConditionalSubscriptionRecord();

  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final SignalSubscriptionState signalSubscriptionState;
  private final ConditionalSubscriptionState conditionalSubscriptionState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  public StartEventSubscriptionManager(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter) {
    processState = processingState.getProcessState();
    messageStartEventSubscriptionState = processingState.getMessageStartEventSubscriptionState();
    signalSubscriptionState = processingState.getSignalSubscriptionState();
    conditionalSubscriptionState = processingState.getConditionalSubscriptionState();
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  public void tryReOpenStartEventSubscription(final DeploymentRecord deploymentRecord) {

    for (final ProcessMetadata processRecord : deploymentRecord.processesMetadata()) {
      if (!processRecord.isDuplicate() && isLatestProcess(processRecord)) {
        closeExistingStartEventSubscriptions(processRecord);
        openStartEventSubscriptions(processRecord);
      }
    }
  }

  private boolean isLatestProcess(final ProcessMetadata processRecord) {
    return processState
            .getLatestProcessVersionByProcessId(
                processRecord.getBpmnProcessIdBuffer(), processRecord.getTenantId())
            .getVersion()
        == processRecord.getVersion();
  }

  private void closeExistingStartEventSubscriptions(final ProcessMetadata processRecord) {
    closeMessageExistingStartEventSubscriptions(processRecord);
    closeSignalExistingStartEventSubscriptions(processRecord);
    closeConditionalExistingStartEventSubscriptions(processRecord);
  }

  public void closeStartEventSubscriptions(final DeployedProcess deployedProcess) {
    if (deployedProcess.getProcess().hasMessageStartEvent()) {
      closeMessageStartEventSubscriptions(deployedProcess);
    }
    if (deployedProcess.getProcess().hasSignalStartEvent()) {
      closeSignalStartEventSubscriptions(deployedProcess);
    }
    if (deployedProcess.getProcess().hasConditionalStartEvent()) {
      closeConditionalStartEventSubscriptions(deployedProcess);
    }
  }

  private void closeConditionalExistingStartEventSubscriptions(
      final ProcessMetadata processRecord) {
    final DeployedProcess lastConditionalProcess =
        findPreviousVersionOfProcess(processRecord, ExecutableCatchEventElement::isConditional);
    if (lastConditionalProcess == null) {
      return;
    }

    closeConditionalStartEventSubscriptions(lastConditionalProcess);
  }

  private void closeConditionalStartEventSubscriptions(final DeployedProcess deployedProcess) {
    conditionalSubscriptionState.visitStartEventSubscriptionsByProcessDefinitionKey(
        deployedProcess.getKey(),
        subscription -> {
          stateWriter.appendFollowUpEvent(
              subscription.getKey(),
              ConditionalSubscriptionIntent.DELETED,
              subscription.getRecord());
          return true;
        });
  }

  private void closeMessageExistingStartEventSubscriptions(final ProcessMetadata processRecord) {
    final DeployedProcess lastMsgProcess =
        findPreviousVersionOfProcess(processRecord, ExecutableCatchEventElement::isMessage);
    if (lastMsgProcess == null) {
      return;
    }

    closeMessageStartEventSubscriptions(lastMsgProcess);
  }

  private void closeMessageStartEventSubscriptions(final DeployedProcess deployedProcess) {
    messageStartEventSubscriptionState.visitSubscriptionsByProcessDefinition(
        deployedProcess.getKey(),
        subscription ->
            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageStartEventSubscriptionIntent.DELETED,
                subscription.getRecord()));
  }

  private void closeSignalExistingStartEventSubscriptions(final ProcessMetadata processRecord) {
    final DeployedProcess lastSignalProcess =
        findPreviousVersionOfProcess(processRecord, ExecutableCatchEventElement::isSignal);
    if (lastSignalProcess == null) {
      return;
    }

    closeSignalStartEventSubscriptions(lastSignalProcess);
  }

  private void closeSignalStartEventSubscriptions(final DeployedProcess deployedProcess) {
    signalSubscriptionState.visitStartEventSubscriptionsByProcessDefinitionKey(
        deployedProcess.getKey(),
        subscription ->
            stateWriter.appendFollowUpEvent(
                subscription.getKey(), SignalSubscriptionIntent.DELETED, subscription.getRecord()));
  }

  private DeployedProcess findPreviousVersionOfProcess(
      final ProcessMetadata processRecord,
      final Predicate<ExecutableCatchEventElement> hasStartEventMatching) {
    final Optional<Integer> processVersionBefore =
        processState.findProcessVersionBefore(
            processRecord.getBpmnProcessId(),
            processRecord.getVersion(),
            processRecord.getTenantId());
    if (processVersionBefore.isEmpty()) {
      return null;
    }

    final var previousVersionOfProcess =
        processState.getProcessByProcessIdAndVersion(
            processRecord.getBpmnProcessIdBuffer(),
            processVersionBefore.get(),
            processRecord.getTenantId());
    final var hasMatchingStartEvent =
        previousVersionOfProcess.getProcess().getStartEvents().stream()
            .anyMatch(hasStartEventMatching);
    if (!hasMatchingStartEvent) {
      return null;
    }
    return previousVersionOfProcess;
  }

  private void openStartEventSubscriptions(final ProcessMetadata processRecord) {
    final long processDefinitionKey = processRecord.getKey();
    final DeployedProcess processDefinition =
        processState.getProcessByKeyAndTenant(processDefinitionKey, processRecord.getTenantId());
    final ExecutableProcess process = processDefinition.getProcess();
    final List<ExecutableStartEvent> startEvents = process.getStartEvents();
    for (final ExecutableStartEvent startEvent : startEvents) {
      if (startEvent.isMessage()) {
        openMessageStartEventSubscription(processDefinition, startEvent);
      } else if (startEvent.isSignal()) {
        openSignalStartEventSubscription(processDefinition, startEvent);
      } else if (startEvent.isConditional()) {
        openConditionalStartEventSubscription(processDefinition, startEvent);
      }
    }
  }

  public void openStartEventSubscriptions(final DeployedProcess deployedProcess) {
    final var process = deployedProcess.getProcess();

    process
        .getStartEvents()
        .forEach(
            startEvent -> {
              if (startEvent.isMessage()) {
                openMessageStartEventSubscription(deployedProcess, startEvent);
              } else if (startEvent.isSignal()) {
                openSignalStartEventSubscription(deployedProcess, startEvent);
              } else if (startEvent.isConditional()) {
                openConditionalStartEventSubscription(deployedProcess, startEvent);
              }
            });
  }

  private void openMessageStartEventSubscription(
      final DeployedProcess processDefinition, final ExecutableStartEvent startEvent) {
    final ExecutableMessage message = startEvent.getMessage();

    message
        .getMessageName()
        .map(BufferUtil::wrapString)
        .ifPresent(
            messageNameBuffer -> {
              messageSubscriptionRecord.reset();
              messageSubscriptionRecord
                  .setMessageName(messageNameBuffer)
                  .setProcessDefinitionKey(processDefinition.getKey())
                  .setBpmnProcessId(processDefinition.getBpmnProcessId())
                  .setStartEventId(startEvent.getId())
                  .setTenantId(processDefinition.getTenantId());

              final var subscriptionKey = keyGenerator.nextKey();
              stateWriter.appendFollowUpEvent(
                  subscriptionKey,
                  MessageStartEventSubscriptionIntent.CREATED,
                  messageSubscriptionRecord);
            });
  }

  private void openSignalStartEventSubscription(
      final DeployedProcess processDefinition, final ExecutableStartEvent startEvent) {
    final ExecutableSignal signal = startEvent.getSignal();

    signal
        .getSignalName()
        .map(BufferUtil::wrapString)
        .ifPresent(
            signalNameBuffer -> {
              signalSubscriptionRecord.reset();
              signalSubscriptionRecord
                  .setSignalName(signalNameBuffer)
                  .setProcessDefinitionKey(processDefinition.getKey())
                  .setBpmnProcessId(processDefinition.getBpmnProcessId())
                  .setCatchEventId(startEvent.getId())
                  .setTenantId(processDefinition.getTenantId());

              final var subscriptionKey = keyGenerator.nextKey();
              stateWriter.appendFollowUpEvent(
                  subscriptionKey, SignalSubscriptionIntent.CREATED, signalSubscriptionRecord);
            });
  }

  private void openConditionalStartEventSubscription(
      final DeployedProcess processDefinition, final ExecutableStartEvent startEvent) {
    final ExecutableConditional conditional = startEvent.getConditional();

    conditionalSubscriptionRecord.reset();
    conditionalSubscriptionRecord
        .setProcessDefinitionKey(processDefinition.getKey())
        .setCatchEventId(startEvent.getId())
        .setCondition(BufferUtil.wrapString(conditional.getCondition()))
        .setVariableNames(conditional.getVariableNames())
        .setTenantId(processDefinition.getTenantId());

    final var subscriptionKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        subscriptionKey, ConditionalSubscriptionIntent.CREATED, conditionalSubscriptionRecord);
  }
}
