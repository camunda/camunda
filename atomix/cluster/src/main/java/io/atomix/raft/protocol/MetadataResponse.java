/*
 * Copyright 2017-present Open Networking Foundation
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.session.SessionMetadata;
import io.atomix.raft.RaftError;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Cluster metadata response. */
public class MetadataResponse extends AbstractRaftResponse {

  private final Set<SessionMetadata> sessions;

  public MetadataResponse(
      final Status status, final RaftError error, final Set<SessionMetadata> sessions) {
    super(status, error);
    this.sessions = sessions;
  }

  /**
   * Returns a new metadata response builder.
   *
   * @return A new metadata response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the session metadata.
   *
   * @return Session metadata.
   */
  public Set<SessionMetadata> sessions() {
    return sessions;
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this).add("status", status).add("sessions", sessions).toString();
    } else {
      return toStringHelper(this).add("status", status).add("error", error).toString();
    }
  }

  /** Metadata response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, MetadataResponse> {

    private Set<SessionMetadata> sessions;

    /**
     * Sets the session metadata.
     *
     * @param sessions The client metadata.
     * @return The metadata response builder.
     */
    public Builder withSessions(final SessionMetadata... sessions) {
      return withSessions(Arrays.asList(checkNotNull(sessions, "sessions cannot be null")));
    }

    /**
     * Sets the session metadata.
     *
     * @param sessions The client metadata.
     * @return The metadata response builder.
     */
    public Builder withSessions(final Collection<SessionMetadata> sessions) {
      this.sessions = new HashSet<>(checkNotNull(sessions, "sessions cannot be null"));
      return this;
    }

    @Override
    public MetadataResponse build() {
      validate();
      return new MetadataResponse(status, error, sessions);
    }

    @Override
    protected void validate() {
      super.validate();
      if (status == Status.OK) {
        checkNotNull(sessions, "sessions cannot be null");
      }
    }
  }
}
