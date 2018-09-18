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

import io.zeebe.logstreams.state.StateController;
import java.io.File;
import org.agrona.ExpandableArrayBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;

public class WorkflowState extends StateController {
  private static final byte[] WORKFLOW_KEY_FAMILY_NAME = "workflowKey".getBytes();
  private static final byte[] WORKFLOW_VERSION_FAMILY_NAME = "workflowVersion".getBytes();

  private static final byte[] LATEST_WORKFLOW_KEY = "latestWorkflowKey".getBytes();

  private ExpandableArrayBuffer keyBuffer;
  private ExpandableArrayBuffer valueBuffer;

  private ColumnFamilyHandle workflowKeyHandle;
  private ColumnFamilyHandle workflowVersionHandle;
  private NextValueManager nextValueManager;

  @Override
  public RocksDB open(final File dbDirectory, final boolean reopen) throws Exception {
    final RocksDB rocksDB = super.open(dbDirectory, reopen);
    keyBuffer = new ExpandableArrayBuffer();
    valueBuffer = new ExpandableArrayBuffer();

    workflowKeyHandle = this.createColumnFamily(WORKFLOW_KEY_FAMILY_NAME);
    workflowVersionHandle = this.createColumnFamily(WORKFLOW_VERSION_FAMILY_NAME);

    nextValueManager = new NextValueManager(this);

    return rocksDB;
  }

  public long getNextWorkflowKey() {
    return nextValueManager.getNextValue(workflowKeyHandle, LATEST_WORKFLOW_KEY);
  }

  public int getNextWorkflowVersion(String bpmnProcessId) {
    return (int) nextValueManager.getNextValue(workflowVersionHandle, bpmnProcessId.getBytes());
  }
}
