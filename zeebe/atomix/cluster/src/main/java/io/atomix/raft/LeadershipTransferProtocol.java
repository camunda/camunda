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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * The rebalancing coordinator's half of the coordinated-leadership-transfer protocol: what it needs
 * to ask a partition's leader to hand leadership over, and to hear back how that went.
 *
 * <p>Named as an interface so that the coordinator, which lives outside Raft, depends on the two
 * messages it exchanges rather than on the messaging that carries them.
 */
@NullMarked
public interface LeadershipTransferProtocol {

  /**
   * Asks {@code leader}, the current leader of the given partition, to transfer its leadership. The
   * response only says whether the transfer was accepted or rejected; a transfer that starts
   * reports its outcome later, through {@link #onResult}.
   */
  CompletableFuture<LeadershipTransferInitiateResponse> initiate(
      MemberId leader,
      String partitionGroup,
      int partitionId,
      LeadershipTransferInitiateRequest request);

  /**
   * Handles the terminal outcome the given partition's leader reports back. A transfer outlives the
   * request that started it, so the outcome arrives as its own request rather than as that
   * request's response, and is correlated by {@link
   * LeadershipTransferResultRequest#correlationId()}.
   */
  void onResult(
      String partitionGroup,
      int partitionId,
      Function<LeadershipTransferResultRequest, CompletableFuture<LeadershipTransferResultResponse>>
          handler);
}
