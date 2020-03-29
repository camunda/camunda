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
import static com.google.common.base.Preconditions.checkArgument;

import io.atomix.raft.RaftError;
import java.util.Objects;

/** Open session response. */
public class OpenSessionResponse extends AbstractRaftResponse {

  protected final long session;
  protected final long timeout;

  public OpenSessionResponse(
      final Status status, final RaftError error, final long session, final long timeout) {
    super(status, error);
    this.session = session;
    this.timeout = timeout;
  }

  /**
   * Returns a new register client response builder.
   *
   * @return A new register client response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the registered session ID.
   *
   * @return The registered session ID.
   */
  public long session() {
    return session;
  }

  /**
   * Returns the session timeout.
   *
   * @return The session timeout.
   */
  public long timeout() {
    return timeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), error, status, session, timeout);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof OpenSessionResponse) {
      final OpenSessionResponse response = (OpenSessionResponse) object;
      return response.status == status
          && Objects.equals(response.error, error)
          && response.session == session
          && response.timeout == timeout;
    }
    return false;
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("session", session)
          .add("timeout", timeout)
          .toString();
    } else {
      return toStringHelper(this).add("status", status).add("error", error).toString();
    }
  }

  /** Register response builder. */
  public static class Builder extends AbstractRaftResponse.Builder<Builder, OpenSessionResponse> {

    private long session;
    private long timeout;

    /**
     * Sets the response session ID.
     *
     * @param session The session ID.
     * @return The register response builder.
     * @throws IllegalArgumentException if {@code session} is less than 1
     */
    public Builder withSession(final long session) {
      checkArgument(session > 0, "session must be positive");
      this.session = session;
      return this;
    }

    /**
     * Sets the session timeout.
     *
     * @param timeout The session timeout.
     * @return The response builder.
     */
    public Builder withTimeout(final long timeout) {
      checkArgument(timeout > 0, "timeout must be positive");
      this.timeout = timeout;
      return this;
    }

    @Override
    public OpenSessionResponse build() {
      validate();
      return new OpenSessionResponse(status, error, session, timeout);
    }

    @Override
    protected void validate() {
      super.validate();
      if (status == Status.OK) {
        checkArgument(session > 0, "session must be positive");
        checkArgument(timeout > 0, "timeout must be positive");
      }
    }
  }
}
