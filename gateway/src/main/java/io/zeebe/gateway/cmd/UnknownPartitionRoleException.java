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
package io.zeebe.gateway.cmd;

import io.zeebe.protocol.PartitionState;

public class UnknownPartitionRoleException extends ClientException {
  private static final String FORMAT =
      "Expected broker role for partition '%d' to be one of [LEADER, FOLLOWER], but got '%s'";

  private final int partitionId;
  private final PartitionState state;

  public UnknownPartitionRoleException(int partitionId, PartitionState state) {
    this(partitionId, state, null);
  }

  public UnknownPartitionRoleException(int partitionId, PartitionState state, Throwable cause) {
    super(String.format(FORMAT, partitionId, state), cause);
    this.partitionId = partitionId;
    this.state = state;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public PartitionState getState() {
    return state;
  }
}
