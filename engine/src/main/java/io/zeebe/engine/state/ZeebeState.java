/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.deployment.DeploymentsState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.engine.state.message.WorkflowInstanceSubscriptionState;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceRelatedIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class ZeebeState {

  private static final String BLACKLIST_INSTANCE_MESSAGE =
      "Blacklist workflow instance {}, due to previous errors.";

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final KeyState keyState;
  private final WorkflowState workflowState;
  private final DeploymentsState deploymentState;
  private final JobState jobState;
  private final MessageState messageState;
  private final MessageSubscriptionState messageSubscriptionState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final WorkflowInstanceSubscriptionState workflowInstanceSubscriptionState;
  private final IncidentState incidentState;
  private final BlackList blackList;
  private final LastProcessedPositionState lastProcessedPositionState;

  private final int partitionId;

  public ZeebeState(final ZeebeDb<ZbColumnFamilies> zeebeDb, final DbContext dbContext) {
    this(Protocol.DEPLOYMENT_PARTITION, zeebeDb, dbContext);
  }

  public ZeebeState(
      final int partitionId, final ZeebeDb<ZbColumnFamilies> zeebeDb, final DbContext dbContext) {
    this.partitionId = partitionId;
    this.zeebeDb = zeebeDb;
    keyState = new KeyState(partitionId, zeebeDb, dbContext);
    workflowState = new WorkflowState(zeebeDb, dbContext, keyState);
    deploymentState = new DeploymentsState(zeebeDb, dbContext);
    jobState = new JobState(zeebeDb, dbContext, partitionId);
    messageState = new MessageState(zeebeDb, dbContext);
    messageSubscriptionState = new MessageSubscriptionState(zeebeDb, dbContext);
    messageStartEventSubscriptionState = new MessageStartEventSubscriptionState(zeebeDb, dbContext);
    workflowInstanceSubscriptionState = new WorkflowInstanceSubscriptionState(zeebeDb, dbContext);
    incidentState = new IncidentState(zeebeDb, dbContext, partitionId);
    blackList = new BlackList(zeebeDb, dbContext);
    lastProcessedPositionState = new LastProcessedPositionState(zeebeDb, dbContext);
  }

  public DeploymentsState getDeploymentState() {
    return deploymentState;
  }

  public WorkflowState getWorkflowState() {
    return workflowState;
  }

  public JobState getJobState() {
    return jobState;
  }

  public MessageState getMessageState() {
    return messageState;
  }

  public MessageSubscriptionState getMessageSubscriptionState() {
    return messageSubscriptionState;
  }

  public MessageStartEventSubscriptionState getMessageStartEventSubscriptionState() {
    return messageStartEventSubscriptionState;
  }

  public WorkflowInstanceSubscriptionState getWorkflowInstanceSubscriptionState() {
    return workflowInstanceSubscriptionState;
  }

  public IncidentState getIncidentState() {
    return incidentState;
  }

  public KeyGenerator getKeyGenerator() {
    return keyState;
  }

  public boolean isOnBlacklist(final TypedRecord record) {
    final UnpackedObject value = record.getValue();
    if (value instanceof WorkflowInstanceRelated) {
      final long workflowInstanceKey = ((WorkflowInstanceRelated) value).getWorkflowInstanceKey();
      if (workflowInstanceKey >= 0) {
        return blackList.isOnBlacklist(workflowInstanceKey);
      }
    }
    return false;
  }

  public boolean tryToBlacklist(
      final TypedRecord<?> typedRecord, final Consumer<Long> onBlacklistingInstance) {
    final Intent intent = typedRecord.getIntent();
    if (shouldBeBlacklisted(intent)) {
      final UnpackedObject value = typedRecord.getValue();
      if (value instanceof WorkflowInstanceRelated) {
        final long workflowInstanceKey = ((WorkflowInstanceRelated) value).getWorkflowInstanceKey();
        blacklist(workflowInstanceKey);
        onBlacklistingInstance.accept(workflowInstanceKey);
      }
    }
    return false;
  }

  private boolean shouldBeBlacklisted(final Intent intent) {

    if (intent instanceof WorkflowInstanceRelatedIntent) {
      final WorkflowInstanceRelatedIntent workflowInstanceRelatedIntent =
          (WorkflowInstanceRelatedIntent) intent;

      return workflowInstanceRelatedIntent.shouldBlacklistInstanceOnError();
    }

    return false;
  }

  private void blacklist(final long workflowInstanceKey) {
    if (workflowInstanceKey >= 0) {
      LOG.warn(BLACKLIST_INSTANCE_MESSAGE, workflowInstanceKey);
      blackList.blacklist(workflowInstanceKey);
    }
  }

  public void markAsProcessed(final long position) {
    lastProcessedPositionState.setPosition(position);
  }

  public long getLastSuccessfulProcessedRecordPosition() {
    return lastProcessedPositionState.getPosition();
  }

  public int getPartitionId() {
    return partitionId;
  }

  public boolean isEmpty(final ZbColumnFamilies column) {
    final var newContext = zeebeDb.createContext();
    return zeebeDb.isEmpty(column, newContext);
  }

  public void visit(
      final ZbColumnFamilies column, final BiConsumer<DirectBuffer, DirectBuffer> visitor) {
    final var newContext = zeebeDb.createContext();
    zeebeDb.visit(column, newContext, visitor);
  }
}
