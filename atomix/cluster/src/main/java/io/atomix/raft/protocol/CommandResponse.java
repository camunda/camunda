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

import io.atomix.raft.RaftError;

/**
 * Client command response.
 *
 * <p>Command responses are sent by servers to clients upon the completion of a {@link
 * CommandRequest}. Command responses are sent with the {@link #index()} (or index) of the state
 * machine at the point at which the command was evaluated. This can be used by the client to ensure
 * it sees state progress monotonically. Note, however, that command responses may not be sent or
 * received in sequential order. If a command response has to await the completion of an event, or
 * if the response is proxied through another server, responses may be received out of order.
 * Clients should resequence concurrent responses to ensure they're handled in FIFO order.
 */
public class CommandResponse extends OperationResponse {

  public CommandResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long eventIndex,
      final byte[] result,
      final long lastSequence) {
    super(status, error, index, eventIndex, result, lastSequence);
  }

  /**
   * Returns a new submit response builder.
   *
   * @return A new submit response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Command response builder. */
  public static class Builder extends OperationResponse.Builder<Builder, CommandResponse> {

    @Override
    public CommandResponse build() {
      validate();
      return new CommandResponse(status, error, index, eventIndex, result, lastSequence);
    }
  }
}
