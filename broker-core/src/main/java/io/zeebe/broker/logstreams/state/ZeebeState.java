/*
 * Zeebe Broker Core
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
package io.zeebe.broker.logstreams.state;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.job.JobState;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.message.state.MessageStartEventSubscriptionState;
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.deployment.distribute.processor.state.DeploymentsState;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.slf4j.Logger;

public class ZeebeState {

  public static final String BLACKLIST_INSTANCE_MESSAGE =
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
}
