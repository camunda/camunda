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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;

/**
 * Base session request.
 *
 * <p>This is the base request for session-related requests. Many client requests are handled within
 * the context of a {@link #session()} identifier.
 */
public abstract class SessionRequest extends AbstractRaftRequest {

  protected final long session;

  protected SessionRequest(final long session) {
    this.session = session;
  }

  /**
   * Returns the session ID.
   *
   * @return The session ID.
   */
  public long session() {
    return session;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), session);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }

    final SessionRequest request = (SessionRequest) object;
    return request.session == session;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("session", session).toString();
  }

  /** Session request builder. */
  public abstract static class Builder<T extends Builder<T, U>, U extends SessionRequest>
      extends AbstractRaftRequest.Builder<T, U> {

    protected long session;

    /**
     * Sets the session ID.
     *
     * @param session The session ID.
     * @return The request builder.
     * @throws IllegalArgumentException if {@code session} is less than 0
     */
    @SuppressWarnings("unchecked")
    public T withSession(final long session) {
      checkArgument(session > 0, "session must be positive");
      this.session = session;
      return (T) this;
    }

    @Override
    protected void validate() {
      checkArgument(session > 0, "session must be positive");
    }
  }
}
