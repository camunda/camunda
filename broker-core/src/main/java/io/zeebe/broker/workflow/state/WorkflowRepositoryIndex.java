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

import io.zeebe.broker.logstreams.processor.JsonSnapshotSupport;
import java.util.HashMap;
import java.util.Map;

public class WorkflowRepositoryIndex
    extends JsonSnapshotSupport<WorkflowRepositoryIndex.WorkflowRepositoryIndexData> {
  public WorkflowRepositoryIndex() {
    super(WorkflowRepositoryIndexData.class);
  }

  public static class WorkflowRepositoryIndexData {
    private long lastGeneratedKey = 0;

    private final Map<String, WorkflowsByBpmnProcessId> bpmnProcessIds = new HashMap<>();

    public Map<String, WorkflowsByBpmnProcessId> getBpmnProcessIds() {
      return bpmnProcessIds;
    }

    public long getLastGeneratedKey() {
      return lastGeneratedKey;
    }

    public void setLastGeneratedKey(final long lastKey) {
      this.lastGeneratedKey = lastKey;
    }
  }

  public static class WorkflowsByBpmnProcessId {
    private int lastGeneratedVersion = 0;

    public int getLastGeneratedVersion() {
      return lastGeneratedVersion;
    }

    public void setLastGeneratedVersion(final int latestVersion) {
      this.lastGeneratedVersion = latestVersion;
    }
  }

  public long getNextKey() {
    final WorkflowRepositoryIndexData data = getData();

    final long nextKey = data.getLastGeneratedKey() + 1;

    data.setLastGeneratedKey(nextKey);

    return nextKey;
  }

  public int getNextVersion(final String bpmnProcessId) {
    final WorkflowsByBpmnProcessId byBpmnProcessId =
        getData()
            .getBpmnProcessIds()
            .computeIfAbsent(bpmnProcessId, (id) -> new WorkflowsByBpmnProcessId());

    final int nextVersion = byBpmnProcessId.getLastGeneratedVersion() + 1;

    byBpmnProcessId.setLastGeneratedVersion(nextVersion);

    return nextVersion;
  }
}
