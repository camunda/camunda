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

public class MinimalTopologyResponseDto {
  private List<MinimalBrokerInfoDto> brokers;
  private String lastCompletedChangeId;

  public boolean isTopologyChangeCompleted(final long changeId) {
    if (lastCompletedChangeId == null) {
      return false;
    }

    final boolean isLatestChangeId = changeId <= Long.parseLong(lastCompletedChangeId);
    final boolean isClusterHealthy =
        brokers.stream().allMatch(MinimalBrokerInfoDto::areAllPartitionsHealthy);

    return isLatestChangeId && isClusterHealthy;
  }

  public List<MinimalBrokerInfoDto> getBrokers() {
    return brokers;
  }

  public void setBrokers(final List<MinimalBrokerInfoDto> brokers) {
    this.brokers = brokers;
  }

  public String getLastCompletedChangeId() {
    return lastCompletedChangeId;
  }

  public void setLastCompletedChangeId(final String lastCompletedChangeId) {
    this.lastCompletedChangeId = lastCompletedChangeId;
  }
}
