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
package io.zeebe.broker.workflow.state;

import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.SubscriptionState;
import io.zeebe.broker.workflow.deployment.data.DeploymentRecord;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;

public class WorkflowState extends StateController {
  private static final byte[] WORKFLOW_KEY_FAMILY_NAME = "workflowKey".getBytes();
  private static final byte[] WORKFLOW_VERSION_FAMILY_NAME = "workflowVersion".getBytes();

  private static final byte[] LATEST_WORKFLOW_KEY = "latestWorkflowKey".getBytes();

  private ColumnFamilyHandle workflowKeyHandle;
  private ColumnFamilyHandle workflowVersionHandle;
  private NextValueManager nextValueManager;
  private WorkflowPersistenceCache workflowPersistenceCache;
  private SubscriptionState<WorkflowSubscription> subscriptionState;

  @Override
  public RocksDB open(final File dbDirectory, final boolean reopen) throws Exception {
    final RocksDB rocksDB = super.open(dbDirectory, reopen);

    workflowKeyHandle = this.createColumnFamily(WORKFLOW_KEY_FAMILY_NAME);
    workflowVersionHandle = this.createColumnFamily(WORKFLOW_VERSION_FAMILY_NAME);

    nextValueManager = new NextValueManager(this);
    workflowPersistenceCache = new WorkflowPersistenceCache(this);
    subscriptionState = new SubscriptionState<>(this, () -> new WorkflowSubscription());

    return rocksDB;
  }

  public long getNextWorkflowKey() {
    return nextValueManager.getNextValue(workflowKeyHandle, LATEST_WORKFLOW_KEY);
  }

  public int getNextWorkflowVersion(String bpmnProcessId) {
    return (int) nextValueManager.getNextValue(workflowVersionHandle, bpmnProcessId.getBytes());
  }

  public boolean putDeployment(long deploymentKey, DeploymentRecord deploymentRecord) {
    return workflowPersistenceCache.putDeployment(deploymentKey, deploymentRecord);
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(
      DirectBuffer bpmnProcessId, int version) {
    return workflowPersistenceCache.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);
  }

  public DeployedWorkflow getWorkflowByKey(long workflowKey) {
    return workflowPersistenceCache.getWorkflowByKey(workflowKey);
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(DirectBuffer bpmnProcessId) {
    return workflowPersistenceCache.getLatestWorkflowVersionByProcessId(bpmnProcessId);
  }

  public Collection<DeployedWorkflow> getWorkflows() {
    return workflowPersistenceCache.getWorkflows();
  }

  public Collection<DeployedWorkflow> getWorkflowsByBpmnProcessId(DirectBuffer processId) {
    return workflowPersistenceCache.getWorkflowsByBpmnProcessId(processId);
  }

  public void put(WorkflowSubscription workflowSubscription) {
    subscriptionState.put(workflowSubscription);
  }

  public void updateCommandSendTime(WorkflowSubscription workflowSubscription) {
    subscriptionState.updateCommandSentTime(workflowSubscription);
  }

  public WorkflowSubscription findSubscription(WorkflowInstanceSubscriptionRecord record) {
    final WorkflowSubscription workflowSubscription =
        new WorkflowSubscription(
            record.getWorkflowInstanceKey(),
            record.getActivityInstanceKey(),
            BufferUtil.cloneBuffer(record.getMessageName()));
    return subscriptionState.getSubscription(workflowSubscription);
  }

  public List<WorkflowSubscription> findSubscriptionsBefore(long time) {
    return subscriptionState.findSubscriptionBefore(time);
  }

  public boolean remove(WorkflowInstanceSubscriptionRecord record) {
    final WorkflowSubscription persistedSubscription = findSubscription(record);

    final boolean exist = persistedSubscription != null;
    if (exist) {
      subscriptionState.remove(persistedSubscription);
    }
    return exist;
  }

  public void remove(WorkflowSubscription workflowSubscription) {
    subscriptionState.remove(workflowSubscription);
  }
}
