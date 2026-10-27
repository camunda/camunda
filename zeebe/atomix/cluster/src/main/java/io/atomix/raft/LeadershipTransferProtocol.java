/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

import io.atomix.cluster.MemberId;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.camunda.cluster.PartitionId;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Allows the rebalancing coordinator to address the leadership transfer protocol from (potentially)
 * outside the Raft group of the partition.
 */
@NullMarked
public interface LeadershipTransferProtocol {

  /**
   * Asks the current leader of the given partition to transfer its leadership. The response only
   * says whether the transfer was accepted or skipped (accepted transfers report their outcome
   * through {@link #onResult}.
   */
  CompletableFuture<LeadershipTransferInitiateResponse> initiate(
      MemberId leader, PartitionId partitionId, LeadershipTransferInitiateRequest request);

  /**
   * Handles the terminal outcome the given partition's leader reports back. The result is
   * correlated to the initiate request by {@link LeadershipTransferResultRequest#correlationId()}.
   */
  void onResult(
      PartitionId partitionId,
      Function<LeadershipTransferResultRequest, CompletableFuture<LeadershipTransferResultResponse>>
          handler);
}
