/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;
import static io.camunda.zeebe.protocol.ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.engine.metrics.ProcessDefinitionMetrics;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionExceptions.ActiveProcessInstancesException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessDeletionBehavior {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDeletionBehavior.class);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final TimerInstanceState timerInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final StartEventSubscriptionManager startEventSubscriptionManager;
  private final StartEventSubscriptions startEventSubscriptions;
  private final ProcessDefinitionMetrics processDefinitionMetrics;
  private final ResourceDeletionAuthorizationBehavior authorizationBehavior;

  ProcessDeletionBehavior(
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CatchEventBehavior catchEventBehavior,
      final StartEventSubscriptionManager startEventSubscriptionManager,
      final StartEventSubscriptions startEventSubscriptions,
      final ProcessDefinitionMetrics processDefinitionMetrics,
      final ResourceDeletionAuthorizationBehavior authorizationBehavior) {
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.keyGenerator = keyGenerator;
    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    bannedInstanceState = processingState.getBannedInstanceState();
    timerInstanceState = processingState.getTimerState();
    this.catchEventBehavior = catchEventBehavior;
    this.startEventSubscriptionManager = startEventSubscriptionManager;
    this.startEventSubscriptions = startEventSubscriptions;
    this.processDefinitionMetrics = processDefinitionMetrics;
    this.authorizationBehavior = authorizationBehavior;
  }

  boolean tryDelete(
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey,
      final DeployedProcess process) {
    command
        .getValue()
        .setResourceType(ResourceType.PROCESS_DEFINITION)
        .setResourceId(process.getBpmnProcessId())
        .setTenantId(process.getTenantId());
    return authorizationBehavior.authorizeAndDelete(
        command,
        eventKey,
        PermissionType.DELETE_PROCESS,
        bufferAsString(process.getBpmnProcessId()),
        process.getTenantId(),
        () -> deleteProcess(process, command, eventKey));
  }

  void deleteProcess(
      final DeployedProcess process,
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey) {
    // We don't add the checksum or resource in this event. The checksum is not easily available
    // and the resources are left out to prevent exceeding the maximum batch size.
    final var processIdBuffer = process.getBpmnProcessId();
    final var tenantId = process.getTenantId();
    final var processRecord =
        new ProcessRecord()
            .setBpmnProcessId(processIdBuffer)
            .setVersion(process.getVersion())
            .setVersionTag(process.getVersionTag())
            .setKey(process.getKey())
            .setResourceName(process.getResourceName())
            .setTenantId(tenantId)
            .setDeploymentKey(process.getDeploymentKey());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ProcessIntent.DELETING, processRecord);

    final String processId = processRecord.getBpmnProcessId();
    final var latestVersion = processState.getLatestProcessVersion(processId, tenantId);

    // If we are deleting the latest version we must unsubscribe the start events
    if (latestVersion == process.getVersion()) {
      unsubscribeStartEvents(process);

      final var previousVersion =
          processState.findProcessVersionBefore(processId, latestVersion, tenantId);
      // If there is a previous version we must resubscribe to the previous version's start events.
      if (previousVersion.isPresent()) {
        final var previousProcess =
            processState.getProcessByProcessIdAndVersion(
                processIdBuffer, previousVersion.get(), tenantId);
        if (previousProcess == null) {
          warnPreviousProcessNotFound(
              processIdBuffer, previousVersion.get(), tenantId, processId, latestVersion);
        } else {
          startEventSubscriptions.resubscribeToStartEvents(previousProcess);
        }
      }
    }

    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(process.getKey(), bannedInstances);

    if (!hasRunningInstances) {
      if (!command.isCommandDistributed() && command.getValue().isDeleteHistory()) {
        deleteProcessInstanceHistory(process.getKey(), eventKey, command.getValue());
      }
      stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ProcessIntent.DELETED, processRecord);
      processDefinitionMetrics.processDefinitionDeleted(process.getKey());
    } else {
      throw new ActiveProcessInstancesException(process.getKey());
    }
  }

  void deleteProcessInstanceHistory(
      final long processDefinitionKey,
      final long eventKey,
      final ResourceDeletionRecord resourceDeletionRecord) {
    final var filter =
        new ProcessInstanceFilter.Builder().processDefinitionKeys(processDefinitionKey).build();
    final long batchOperationKey = keyGenerator.nextKey();
    final var batchOperationRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setAuthentication(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(CamundaAuthentication.anonymous())))
            .setFollowUpCommand(
                ValueType.HISTORY_DELETION,
                HistoryDeletionIntent.DELETE,
                new HistoryDeletionRecord()
                    .setResourceKey(processDefinitionKey)
                    .setResourceType(HistoryDeletionType.PROCESS_DEFINITION));
    commandWriter.appendFollowUpCommand(
        eventKey, BatchOperationIntent.CREATE, batchOperationRecord);

    resourceDeletionRecord.setBatchOperationKey(batchOperationKey);
    resourceDeletionRecord.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);
  }

  private void unsubscribeStartEvents(final DeployedProcess deployedProcess) {
    final var process = deployedProcess.getProcess();
    if (process.hasTimerStartEvent()) {
      timerInstanceState.forEachTimerForElementInstance(
          NO_ELEMENT_INSTANCE,
          timer -> {
            if (timer.getProcessDefinitionKey() == deployedProcess.getKey()) {
              catchEventBehavior.unsubscribeFromTimerEvent(timer);
            }
          });
    }

    startEventSubscriptionManager.closeStartEventSubscriptions(deployedProcess);
  }

  private void warnPreviousProcessNotFound(
      final DirectBuffer processIdBuffer,
      final int previousVersion,
      final String tenantId,
      final String processId,
      final int latestVersion) {
    LOG.warn(
        "Expected to find previous process: {} with version: {} and tenant: '{}' to "
            + "resubscribe start events, but no row exists in {}. "
            + "knownVersions: {}, current latest version: {}.",
        bufferAsString(processIdBuffer),
        previousVersion,
        tenantId,
        PROCESS_CACHE_BY_ID_AND_VERSION.name(),
        processState.getKnownProcessVersions(processId, tenantId),
        latestVersion);
  }
}
