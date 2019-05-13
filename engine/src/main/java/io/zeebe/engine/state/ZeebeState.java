/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state;

import static io.zeebe.engine.processor.StreamProcessor.NO_EVENTS_PROCESSED;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
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
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.slf4j.Logger;

public class ZeebeState {

  private static final String LAST_PROCESSED_EVENT_KEY = "LAST_PROCESSED_EVENT_KEY";
  private static final String BLACKLIST_INSTANCE_MESSAGE =
      "Blacklist workflow instance {}, due to previous errors.";

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

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

  private final DbString lastProcessedEventKey;
  private final DbLong lastProcessedEventPosition;
  private final ColumnFamily<DbString, DbLong> lastProcessedRecordPositionColumnFamily;

  public ZeebeState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    this(Protocol.DEPLOYMENT_PARTITION, zeebeDb, dbContext);
  }

  public ZeebeState(int partitionId, ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    keyState = new KeyState(partitionId, zeebeDb, dbContext);
    workflowState = new WorkflowState(zeebeDb, dbContext, keyState);
    deploymentState = new DeploymentsState(zeebeDb, dbContext);
    jobState = new JobState(zeebeDb, dbContext);
    messageState = new MessageState(zeebeDb, dbContext);
    messageSubscriptionState = new MessageSubscriptionState(zeebeDb, dbContext);
    messageStartEventSubscriptionState = new MessageStartEventSubscriptionState(zeebeDb, dbContext);
    workflowInstanceSubscriptionState = new WorkflowInstanceSubscriptionState(zeebeDb, dbContext);
    incidentState = new IncidentState(zeebeDb, dbContext);
    blackList = new BlackList(zeebeDb, dbContext);

    lastProcessedEventKey = new DbString();
    lastProcessedEventKey.wrapString(LAST_PROCESSED_EVENT_KEY);
    lastProcessedEventPosition = new DbLong();
    lastProcessedRecordPositionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEFAULT, dbContext, lastProcessedEventKey, lastProcessedEventPosition);
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

  public void blacklist(long workflowInstanceKey) {
    if (workflowInstanceKey >= 0) {
      LOG.warn(BLACKLIST_INSTANCE_MESSAGE, workflowInstanceKey);
      blackList.blacklist(workflowInstanceKey);
    }
  }

  public boolean isOnBlacklist(TypedRecord record) {
    final UnpackedObject value = record.getValue();
    if (value instanceof WorkflowInstanceRelated) {
      final long workflowInstanceKey = ((WorkflowInstanceRelated) value).getWorkflowInstanceKey();
      if (workflowInstanceKey >= 0) {
        return blackList.isOnBlacklist(workflowInstanceKey);
      }
    }
    return false;
  }

  public void markAsProcessed(long position) {
    lastProcessedEventPosition.wrapLong(position);
    lastProcessedRecordPositionColumnFamily.put(lastProcessedEventKey, lastProcessedEventPosition);
  }

  public long getLastSuccessfuProcessedRecordPosition() {
    final DbLong position = lastProcessedRecordPositionColumnFamily.get(lastProcessedEventKey);
    return position != null ? position.getValue() : NO_EVENTS_PROCESSED;
  }
}
