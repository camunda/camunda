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
 * Event reset request.
 *
 * <p>Reset requests are sent by clients to servers if the client receives an event message out of
 * sequence to force the server to resend events from the correct index.
 */
public class ResetRequest extends SessionRequest {

  private final long index;

  public ResetRequest(final long session, final long index) {
    super(session);
    this.index = index;
  }

  /**
   * Returns a new publish response builder.
   *
   * @return A new publish response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the event index.
   *
   * @return The event index.
   */
  public long index() {
    return index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), session, index);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof ResetRequest) {
      final ResetRequest request = (ResetRequest) object;
      return request.session == session && request.index == index;
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("session", session).add("index", index).toString();
  }

  /** Reset request builder. */
  public static class Builder extends SessionRequest.Builder<Builder, ResetRequest> {

    private long index;

    /**
     * Sets the event index.
     *
     * @param index The event index.
     * @return The response builder.
     * @throws IllegalArgumentException if {@code index} is less than {@code 1}
     */
    public Builder withIndex(final long index) {
      checkArgument(index >= 0, "index must be positive");
      this.index = index;
      return this;
    }

    /** @throws IllegalStateException if sequence is less than 1 */
    @Override
    public ResetRequest build() {
      validate();
      return new ResetRequest(session, index);
    }
  }
}
