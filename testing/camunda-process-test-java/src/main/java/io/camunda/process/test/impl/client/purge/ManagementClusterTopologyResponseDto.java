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
  private List<ManagementPhysicalTenantDto> physicalTenants;

  /**
   * Returns whether the topology change identified by {@code changeId} has completed and the
   * cluster is healthy, or that this response cannot tell.
   */
  public ChangeCompletion getChangeCompletion(final long changeId) {
    if (lastChange != null) {
      final boolean isLatestChangeId = changeId <= lastChange.getId();
      final boolean isClusterHealthy =
          pendingChange == null
              && brokers != null
              && brokers.stream().allMatch(ManagementBrokerStateDto::isActive);

      return isLatestChangeId && isClusterHealthy
          ? ChangeCompletion.COMPLETED
          : ChangeCompletion.NOT_COMPLETED;
    }

    return coversMultiplePhysicalTenants()
        ? ChangeCompletion.NOT_REPORTED
        : ChangeCompletion.NOT_COMPLETED;
  }

  /**
   * Returns a physical tenant to scope a follow-up topology request to, or {@code null} if this
   * response names none.
   */
  public String getFirstPhysicalTenantId() {
    if (physicalTenants == null) {
      return null;
    }

    return physicalTenants.stream()
        .map(ManagementPhysicalTenantDto::getId)
        .filter(id -> id != null && !id.isEmpty())
        .findFirst()
        .orElse(null);
  }

  /**
   * The topology reports {@code lastChange}, {@code pendingChange} and {@code routing} only for a
   * response covering a single physical tenant, because that state is cluster-wide and has no
   * single physical tenant to be scoped to. More than one entry under {@code physicalTenants} is
   * what distinguishes such a response from a single-tenant one, whose {@code physicalTenants}
   * holds at most one entry.
   */
  private boolean coversMultiplePhysicalTenants() {
    return physicalTenants != null && physicalTenants.size() > 1;
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

  public List<ManagementPhysicalTenantDto> getPhysicalTenants() {
    return physicalTenants;
  }

  public void setPhysicalTenants(final List<ManagementPhysicalTenantDto> physicalTenants) {
    this.physicalTenants = physicalTenants;
  }

  /** What a cluster topology response says about the completion of a topology change. */
  public enum ChangeCompletion {
    /** The change has completed and the cluster is healthy again. */
    COMPLETED,
    /** The change has not completed yet, or the cluster is not healthy again yet. */
    NOT_COMPLETED,
    /**
     * The response carries no cluster-wide {@code lastChange} because it covers more than one
     * physical tenant. Completion has to be read from a response scoped to a physical tenant.
     */
    NOT_REPORTED
  }
}
