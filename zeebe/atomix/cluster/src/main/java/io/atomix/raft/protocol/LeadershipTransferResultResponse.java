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
package io.atomix.raft.protocol;

import io.atomix.raft.RaftError;

/**
 * A simple acknowledgement that the coordinator received a {@link LeadershipTransferResultRequest}.
 * The reporting leader treats the notification as best-effort and does not act on this ack.
 */
public class LeadershipTransferResultResponse extends AbstractRaftResponse {

  public LeadershipTransferResultResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Leadership-transfer result response builder. */
  public static class Builder
      extends AbstractRaftResponse.Builder<Builder, LeadershipTransferResultResponse> {

    @Override
    public LeadershipTransferResultResponse build() {
      validate();
      return new LeadershipTransferResultResponse(status, error);
    }
  }
}
