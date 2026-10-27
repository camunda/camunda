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
 * TimeoutNow response.
 *
 * <p>A simple acknowledgement that the recipient accepted the request and will start an election.
 * This does not signify that a leadership transfer was completed - the current leader should detect
 * a successful transfer by observing the term advance (i.e. by losing leadership). See {@link
 * TimeoutNowRequest}.
 */
public class TimeoutNowResponse extends AbstractRaftResponse {

  public TimeoutNowResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** TimeoutNow response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, TimeoutNowResponse> {

    @Override
    public TimeoutNowResponse build() {
      validate();
      return new TimeoutNowResponse(status, error);
    }
  }
}
