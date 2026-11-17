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

/** Minimal representation of a broker in the management cluster topology. */
public class ManagementBrokerStateDto {

  private String state;
  private List<ManagementPartitionStateDto> partitions;

  public boolean isActive() {
    return partitions != null
        && partitions.stream().allMatch(ManagementPartitionStateDto::isActive);
  }

  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  public List<ManagementPartitionStateDto> getPartitions() {
    return partitions;
  }

  public void setPartitions(final List<ManagementPartitionStateDto> partitions) {
    this.partitions = partitions;
  }
}
