/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.requireNoPendingMsgSubMigrationDistribution;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.ProcessInstanceMigrationPreconditionFailedException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class ProcessInstanceMigrationCatchEventBehaviour {

  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final CatchEventBehavior catchEventBehavior;
  private final TypedCommandWriter commandWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final DistributionState distributionState;
  private final StateWriter stateWriter;
  private final int currentPartitionId;
  private final RoutingInfo routingInfo;

  public ProcessInstanceMigrationCatchEventBehaviour(
      final ProcessMessageSubscriptionState processMessageSubscriptionState,
      final CatchEventBehavior catchEventBehavior,
      final TypedCommandWriter commandWriter,
      final CommandDistributionBehavior commandDistributionBehavior,
      final DistributionState distributionState,
      final StateWriter stateWriter,
      final int currentPartitionId,
      final RoutingInfo routingInfo) {
    this.processMessageSubscriptionState = processMessageSubscriptionState;
    this.catchEventBehavior = catchEventBehavior;
    this.commandWriter = commandWriter;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.distributionState = distributionState;
    this.stateWriter = stateWriter;
    this.currentPartitionId = currentPartitionId;
    this.routingInfo = routingInfo;
  }

  /**
   * Unsubscribes the element instance from unmapped catch events in the source process, and
   * subscribes it to unmapped catch events in the target process.
   *
   * <p>It also migrates event subscriptions for mapped catch events.
   */
  public void handleCatchEvents(
      final ElementInstance elementInstance,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ProcessInstanceRecord elementInstanceRecord,
      final String targetElementId,
      final long processInstanceKey,
      final String elementId) {
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstanceRecord, elementInstance.getState());

    // set type explicitly for multi-instance body because inner activities has the same element id
    final var expectedType =
        elementInstanceRecord.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY
            ? ExecutableMultiInstanceBody.class
            : ExecutableCatchEventSupplier.class;
    final ExecutableCatchEventSupplier targetElement =
        targetProcessDefinition.getProcess().getElementById(targetElementId, expectedType);

    // UNSUBSCRIBE FROM CATCH EVENTS
    final List<ProcessMessageSubscription> processMessageSubscriptionsToMigrate =
        unsubscribeFromMessageEvents(
            elementInstance, sourceElementIdToTargetElementId, processInstanceKey, elementId);
    final List<TimerInstance> timerInstancesToMigrate =
        unsubscribeFromTimerEvents(elementInstance, sourceElementIdToTargetElementId);
    final List<SignalSubscription> signalSubscriptionsToMigrate =
        unsubscribeFromSignalEvents(elementInstance, sourceElementIdToTargetElementId);

    // SUBSCRIBE TO CATCH EVENTS
    final Map<String, Boolean> targetCatchEventIdToInterrupting =
        subscribeToAllCatchEvents(
            elementInstance,
            sourceElementIdToTargetElementId,
            elementInstanceRecord,
            targetElementId,
            processInstanceKey,
            elementId,
            context,
            targetElement);

    // MIGRATE CATCH EVENT SUBSCRIPTIONS
    migrateMessageEvents(
        targetProcessDefinition,
        sourceElementIdToTargetElementId,
        processMessageSubscriptionsToMigrate,
        targetCatchEventIdToInterrupting);
    migrateTimerEvents(
        targetProcessDefinition, sourceElementIdToTargetElementId, timerInstancesToMigrate);
    migrateSignalEvents(
        targetProcessDefinition, sourceElementIdToTargetElementId, signalSubscriptionsToMigrate);
  }

  private void migrateSignalEvents(
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final List<SignalSubscription> signalSubscriptionsToMigrate) {
    signalSubscriptionsToMigrate.forEach(
        signalSubscription -> {
          final var sourceCatchEventId = signalSubscription.getRecord().getCatchEventId();
          final var targetCatchEventId = sourceElementIdToTargetElementId.get(sourceCatchEventId);

          final var signalSubscriptionRecord = signalSubscription.getRecord();
          final var signalSubscriptionRecordCopy = new SignalSubscriptionRecord();
          signalSubscriptionRecordCopy.wrap(signalSubscriptionRecord);
          signalSubscriptionRecordCopy.setProcessDefinitionKey(targetProcessDefinition.getKey());
          signalSubscriptionRecordCopy.setCatchEventId(BufferUtil.wrapString(targetCatchEventId));
          signalSubscriptionRecordCopy.setBpmnProcessId(targetProcessDefinition.getBpmnProcessId());

          stateWriter.appendFollowUpEvent(
              signalSubscription.getKey(),
              SignalSubscriptionIntent.MIGRATED,
              signalSubscriptionRecordCopy);
        });
  }

  private void migrateTimerEvents(
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final List<TimerInstance> timerInstancesToMigrate) {
    timerInstancesToMigrate.forEach(
        timerInstance -> {
          final var sourceCatchEventId =
              BufferUtil.bufferAsString(timerInstance.getHandlerNodeId());
          final var targetCatchEventId = sourceElementIdToTargetElementId.get(sourceCatchEventId);

          final TimerRecord timerRecord = new TimerRecord();
          timerRecord.setElementInstanceKey(timerInstance.getElementInstanceKey());
          timerRecord.setProcessInstanceKey(timerInstance.getProcessInstanceKey());
          timerRecord.setDueDate(timerInstance.getDueDate());
          timerRecord.setTargetElementId(BufferUtil.wrapString(targetCatchEventId));
          timerRecord.setRepetitions(timerInstance.getRepetitions());
          timerRecord.setProcessDefinitionKey(targetProcessDefinition.getKey());
          timerRecord.setTenantId(timerInstance.getTenantId());

          stateWriter.appendFollowUpEvent(
              timerInstance.getKey(), TimerIntent.MIGRATED, timerRecord);
        });
  }

  private void migrateMessageEvents(
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final List<ProcessMessageSubscription> processMessageSubscriptionsToMigrate,
      final Map<String, Boolean> targetCatchEventIdToInterrupting) {
    processMessageSubscriptionsToMigrate.forEach(
        processMessageSubscription ->
            migrateMessageSubscription(
                targetProcessDefinition,
                sourceElementIdToTargetElementId,
                processMessageSubscription,
                targetCatchEventIdToInterrupting));
  }

  private Map<String, Boolean> subscribeToAllCatchEvents(
      final ElementInstance elementInstance,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ProcessInstanceRecord elementInstanceRecord,
      final String targetElementId,
      final long processInstanceKey,
      final String elementId,
      final BpmnElementContextImpl context,
      final ExecutableCatchEventSupplier targetElement) {
    final Map<String, Boolean> targetCatchEventIdToInterrupting = new HashMap<>();
    catchEventBehavior
        .subscribeToEvents(
            context,
            targetElement,
            executableCatchEvent -> {
              final String targetCatchEventId =
                  BufferUtil.bufferAsString(executableCatchEvent.getId());
              if (sourceElementIdToTargetElementId.containsValue(targetCatchEventId)) {
                // We will migrate this mapped catch event, so we don't want to subscribe to it
                // Store interrupting status, we will use it to update the interrupting status of
                // the migrated subscription
                targetCatchEventIdToInterrupting.put(
                    targetCatchEventId, executableCatchEvent.isInterrupting());
                return false;
              }

              if (elementInstance.isInterrupted()) {
                // The flow scope is interrupted, so we shouldn't subscribe to any catch event
                return false;
              }

              return true;
            },
            catchEvent -> {
              final var element = catchEvent.element();
              final String targetCatchEventId = BufferUtil.bufferAsString(element.getId());

              if (element.isMessage()) {
                requireNoSubscriptionForMessage(
                    elementInstance,
                    catchEvent.messageName(),
                    elementInstanceRecord.getTenantId(),
                    targetCatchEventId);
              }
              return true;
            })
        .ifLeft(
            failure -> {
              throw new ProcessInstanceMigrationPreconditionFailedException(
                  """
                  Expected to migrate process instance '%s' \
                  but active element with id '%s' is mapped to element with id '%s' \
                  that must be subscribed to a catch event. %s"""
                      .formatted(
                          processInstanceKey, elementId, targetElementId, failure.getMessage()),
                  RejectionType.INVALID_STATE);
            });
    return targetCatchEventIdToInterrupting;
  }

  private List<SignalSubscription> unsubscribeFromSignalEvents(
      final ElementInstance elementInstance,
      final Map<String, String> sourceElementIdToTargetElementId) {
    final List<SignalSubscription> signalSubscriptionsToMigrate = new ArrayList<>();
    catchEventBehavior.unsubscribeFromSignalEventsBySubscriptionFilter(
        elementInstance.getKey(),
        signalSubscription -> {
          if (sourceElementIdToTargetElementId.containsKey(
              signalSubscription.getRecord().getCatchEventId())) {
            // We will migrate this mapped catch event, so we don't want to unsubscribe from it.
            // Avoid reusing the subscription directly as any access to the state (e.g. #get) will
            // overwrite it
            final var copy = new SignalSubscription();
            copy.copyFrom(signalSubscription);
            signalSubscriptionsToMigrate.add(copy);

            return false;
          }

          return true;
        });
    return signalSubscriptionsToMigrate;
  }

  private List<TimerInstance> unsubscribeFromTimerEvents(
      final ElementInstance elementInstance,
      final Map<String, String> sourceElementIdToTargetElementId) {
    final List<TimerInstance> timerInstancesToMigrate = new ArrayList<>();
    catchEventBehavior.unsubscribeFromTimerEventsByInstanceFilter(
        elementInstance.getKey(),
        timerInstance -> {
          if (sourceElementIdToTargetElementId.containsKey(
              BufferUtil.bufferAsString(timerInstance.getHandlerNodeId()))) {
            // We will migrate this mapped catch event, so we don't want to unsubscribe from it.
            // Avoid reusing the subscription directly as any access to the state (e.g. #get) will
            // overwrite it
            final var copy = new TimerInstance();
            copy.copyFrom(timerInstance);
            timerInstancesToMigrate.add(copy);

            return false;
          }

          return true;
        });
    return timerInstancesToMigrate;
  }

  private List<ProcessMessageSubscription> unsubscribeFromMessageEvents(
      final ElementInstance elementInstance,
      final Map<String, String> sourceElementIdToTargetElementId,
      final long processInstanceKey,
      final String elementId) {
    final List<ProcessMessageSubscription> processMessageSubscriptionsToMigrate = new ArrayList<>();
    catchEventBehavior.unsubscribeFromMessageEvents(
        elementInstance.getKey(),
        subscription -> {
          final long distributionKey = subscription.getKey();
          requireNoPendingMsgSubMigrationDistribution(
              distributionState,
              distributionKey,
              elementId,
              processInstanceKey,
              subscription.getRecord().getElementId());

          final var catchEventId = subscription.getRecord().getElementId();
          if (sourceElementIdToTargetElementId.containsKey(catchEventId)) {
            // We will migrate this mapped catch event, so we don't want to unsubscribe from it
            // avoid reusing the subscription directly as any access to the state (e.g. #get) will
            // overwrite it
            final var copySubscription = new ProcessMessageSubscription();
            copySubscription.copyFrom(subscription);
            processMessageSubscriptionsToMigrate.add(copySubscription);
            return false;
          }

          return true;
        });
    return processMessageSubscriptionsToMigrate;
  }

  private void migrateMessageSubscription(
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ProcessMessageSubscription processMessageSubscription,
      final Map<String, Boolean> targetCatchEventIdToInterrupting) {
    final var processMessageSubscriptionRecord = processMessageSubscription.getRecord();
    final var sourceCatchEventId = processMessageSubscriptionRecord.getElementId();
    final var targetCatchEventId = sourceElementIdToTargetElementId.get(sourceCatchEventId);
    final Boolean interrupting = targetCatchEventIdToInterrupting.get(targetCatchEventId);

    final var messageSubscription =
        new MessageSubscriptionRecord()
            .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
            .setElementInstanceKey(processMessageSubscriptionRecord.getElementInstanceKey())
            .setProcessInstanceKey(processMessageSubscriptionRecord.getProcessInstanceKey())
            .setMessageName(processMessageSubscriptionRecord.getMessageNameBuffer())
            .setCorrelationKey(processMessageSubscriptionRecord.getCorrelationKeyBuffer())
            .setTenantId(processMessageSubscriptionRecord.getTenantId());

    if (interrupting != null) {
      processMessageSubscriptionRecord.setInterrupting(interrupting);
      messageSubscription.setInterrupting(interrupting);
    }

    stateWriter.appendFollowUpEvent(
        processMessageSubscription.getKey(),
        ProcessMessageSubscriptionIntent.MIGRATED,
        processMessageSubscriptionRecord
            .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
            .setElementId(BufferUtil.wrapString(targetCatchEventId)));

    final var subscriptionPartitionId =
        routingInfo.partitionForCorrelationKey(
            BufferUtil.wrapString(messageSubscription.getCorrelationKey()));

    final long distributionKey = processMessageSubscription.getKey();
    if (currentPartitionId == subscriptionPartitionId) {
      commandWriter.appendFollowUpCommand(
          distributionKey, MessageSubscriptionIntent.MIGRATE, messageSubscription);
    } else {
      commandDistributionBehavior
          .withKey(distributionKey)
          .unordered()
          .forPartition(processMessageSubscriptionRecord.getSubscriptionPartitionId())
          .distribute(
              ValueType.MESSAGE_SUBSCRIPTION,
              MessageSubscriptionIntent.MIGRATE,
              messageSubscription);
    }
  }

  private void requireNoSubscriptionForMessage(
      final ElementInstance elementInstance,
      final DirectBuffer messageName,
      final String tenantId,
      final String targetCatchEventId) {
    final boolean existSubscriptionForMessageName =
        processMessageSubscriptionState.existSubscriptionForElementInstance(
            elementInstance.getKey(), messageName, tenantId);
    ProcessInstanceMigrationPreconditions.requireNoSubscriptionForMessage(
        existSubscriptionForMessageName, elementInstance, messageName, targetCatchEventId);
  }
}
