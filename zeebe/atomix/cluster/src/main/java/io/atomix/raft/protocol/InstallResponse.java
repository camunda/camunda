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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;

import io.atomix.raft.RaftError;

/**
 * Snapshot installation response.
 *
 * <p>Install responses are sent once a snapshot installation request has been received and
 * processed. Install responses provide no additional metadata aside from indicating whether or not
 * the request was successful.
 */
public class InstallResponse extends AbstractRaftResponse {

  protected int preferredChunkSize;

  public InstallResponse(final Status status, final RaftError error, final int preferredChunkSize) {
    super(status, error);
    this.preferredChunkSize = preferredChunkSize;
  }

  public int preferredChunkSize() {
    return preferredChunkSize;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("status", status)
        .add("error", error)
        .add("preferredChunkSize", preferredChunkSize)
        .toString();
  }

  /**
   * Returns a new install response builder.
   *
   * @return A new install response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Install response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, InstallResponse> {
    protected int preferredChunkSize;

    @Override
    public InstallResponse build() {
      validate();
      checkArgument(preferredChunkSize >= 0, "preferred chunk size must be positive");
      return new InstallResponse(status, error, preferredChunkSize);
    }

    public Builder withPreferredChunkSize(final int preferredChunkSize) {
      this.preferredChunkSize = preferredChunkSize;
      return this;
    }
  }
}
