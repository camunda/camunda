/*
 * Copyright 2015-present Open Networking Foundation
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.RaftError;
import io.atomix.raft.cluster.RaftMember;
import java.util.Collection;

/** Server join configuration change response. */
public class JoinResponse extends ConfigurationResponse {

  public JoinResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long term,
      final long timestamp,
      final Collection<RaftMember> members) {
    super(status, error, index, term, timestamp, members);
  }

  /**
   * Returns a new join response builder.
   *
   * @return A new join response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Join response builder. */
  public static class Builder extends ConfigurationResponse.Builder<Builder, JoinResponse> {

    @Override
    public JoinResponse build() {
      validate();
      return new JoinResponse(status, error, index, term, timestamp, members);
    }

    @Override
    protected void validate() {
      // JoinResponse allows null errors indicating the client should retry.
      checkNotNull(status, "status cannot be null");
      if (status == Status.OK) {
        checkArgument(index >= 0, "index must be positive");
        checkArgument(term >= 0, "term must be positive");
        checkArgument(timestamp > 0, "time must be positive");
        checkNotNull(members, "members cannot be null");
      }
    }
  }
}
