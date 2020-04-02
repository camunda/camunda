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
 * Client query response.
 *
 * <p>Query responses are sent by servers to clients upon the completion of a {@link QueryRequest}.
 * Query responses are sent with the {@link #index()} of the state machine at the point at which the
 * query was evaluated. This can be used by the client to ensure it sees state progress
 * monotonically. Note, however, that query responses may not be sent or received in sequential
 * order. If a query response is proxied through another server, responses may be received out of
 * order. Clients should resequence concurrent responses to ensure they're handled in FIFO order.
 */
public class QueryResponse extends OperationResponse {

  public QueryResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long eventIndex,
      final byte[] result,
      final long lastSequence) {
    super(status, error, index, eventIndex, result, lastSequence);
  }

  /**
   * Returns a new query response builder.
   *
   * @return A new query response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Query response builder. */
  public static class Builder extends OperationResponse.Builder<Builder, QueryResponse> {

    @Override
    public QueryResponse build() {
      validate();
      return new QueryResponse(status, error, index, eventIndex, result, lastSequence);
    }
  }
}
