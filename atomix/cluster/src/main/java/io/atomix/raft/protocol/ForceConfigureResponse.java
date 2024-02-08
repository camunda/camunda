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
 * limitations under the License
 */
package io.atomix.raft.protocol;

import io.atomix.raft.RaftError;

/** Force Configuration response. */
public class ForceConfigureResponse extends AbstractRaftResponse {

  /** Updated configuration index of this member */
  private final long index;

  /** Current term of this member */
  private final long term;

  public ForceConfigureResponse(
      final Status status, final RaftError error, final long index, final long term) {
    super(status, error);
    this.index = index;
    this.term = term;
  }

  public long index() {
    return index;
  }

  public long term() {
    return term;
  }

  /**
   * Returns a new configure response builder.
   *
   * @return A new configure response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder
      extends AbstractRaftResponse.Builder<Builder, ForceConfigureResponse> {

    private long index;
    private long term;

    /**
     * Sets the response index.
     *
     * @param index updated configuration index
     * @return The response builder.
     */
    public Builder withIndex(final long index) {
      this.index = index;
      return this;
    }

    /**
     * Sets the response term.
     *
     * @param term current term of this member
     * @return The response builder.
     */
    public Builder withTerm(final long term) {
      this.term = term;
      return this;
    }

    @Override
    public ForceConfigureResponse build() {
      return new ForceConfigureResponse(status, error, index, term);
    }
  }
}
