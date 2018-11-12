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

import io.zeebe.broker.job.JobState;
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.util.KeyStateController;
import io.zeebe.broker.workflow.deployment.distribute.processor.state.DeploymentsState;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import java.io.File;
import java.util.List;

public class ZeebeState extends KeyStateController {

  private final WorkflowState workflowState = new WorkflowState();
  private final DeploymentsState deploymentState = new DeploymentsState();
  private final JobState jobState = new JobState();
  private final MessageState messageState = new MessageState();
  private final MessageSubscriptionState messageSubscriptionState = new MessageSubscriptionState();
  private final WorkflowInstanceSubscriptionState workflowInstanceSubscriptionState =
      new WorkflowInstanceSubscriptionState();

  @Override
  public ZbRocksDb open(final File dbDirectory, final boolean reopen) throws Exception {
    final List<byte[]> columnFamilyNames = WorkflowState.getColumnFamilyNames();
    columnFamilyNames.addAll(DeploymentsState.getColumnFamilyNames());
    columnFamilyNames.addAll(JobState.getColumnFamilyNames());
    columnFamilyNames.addAll(MessageState.getColumnFamilyNames());
    columnFamilyNames.addAll(MessageSubscriptionState.getColumnFamilyNames());
    columnFamilyNames.addAll(WorkflowInstanceSubscriptionState.getColumnFamilyNames());

    final ZbRocksDb rocksDB = super.open(dbDirectory, reopen, columnFamilyNames);

    workflowState.onOpened(this);
    deploymentState.onOpened(this);
    jobState.onOpened(this);
    messageState.onOpened(this);
    messageSubscriptionState.onOpened(this);
    workflowInstanceSubscriptionState.onOpened(this);

    return rocksDB;
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

  public WorkflowInstanceSubscriptionState getWorkflowInstanceSubscriptionState() {
    return workflowInstanceSubscriptionState;
  }
}
