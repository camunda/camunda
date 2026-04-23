/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.client.purge;

import java.util.List;

/**
 * Minimal representation of the management cluster topology response returned by {@code GET
 * /actuator/cluster}.
 */
public class ManagementClusterTopologyResponseDto {

  private List<ManagementBrokerStateDto> brokers;
  private ManagementCompletedChangeDto lastChange;
  private Object pendingChange;

  /**
   * Returns true if the topology change identified by {@code changeId} has completed and the
   * cluster is healthy.
   */
  public boolean isTopologyChangeCompleted(final long changeId) {
    if (lastChange == null) {
      return false;
    }

    final boolean isLatestChangeId = changeId <= lastChange.getId();
    final boolean isClusterHealthy =
        pendingChange == null
            && brokers != null
            && brokers.stream().allMatch(ManagementBrokerStateDto::isActive);

    return isLatestChangeId && isClusterHealthy;
  }

  public List<ManagementBrokerStateDto> getBrokers() {
    return brokers;
  }

  public void setBrokers(final List<ManagementBrokerStateDto> brokers) {
    this.brokers = brokers;
  }

  public ManagementCompletedChangeDto getLastChange() {
    return lastChange;
  }

  public void setLastChange(final ManagementCompletedChangeDto lastChange) {
    this.lastChange = lastChange;
  }

  public Object getPendingChange() {
    return pendingChange;
  }

  public void setPendingChange(final Object pendingChange) {
    this.pendingChange = pendingChange;
  }
}
