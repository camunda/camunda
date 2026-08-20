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

/** Leadership transfer response. */
public class TransferResponse extends AbstractRaftResponse {

  public TransferResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  /**
   * Returns a new transfer response builder.
   *
   * @return A new transfer response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Join response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, TransferResponse> {

    @Override
    public TransferResponse build() {
      validate();
      return new TransferResponse(status, error);
    }
  }
}
