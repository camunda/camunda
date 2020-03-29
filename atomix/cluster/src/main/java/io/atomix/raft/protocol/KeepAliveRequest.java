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
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.utils.misc.ArraySizeHashPrinter;
import java.util.Arrays;
import java.util.Objects;

/**
 * Session keep alive request.
 *
 * <p>Keep alive requests are sent by clients to servers to maintain a session registered via a
 * {@link OpenSessionRequest}. Once a session has been registered, clients are responsible for
 * sending keep alive requests to the cluster at a rate less than the provided {@link
 * OpenSessionResponse#timeout()}. Keep alive requests also server to acknowledge the receipt of
 * responses and events by the client. The {@link #commandSequenceNumbers()} number indicates the
 * highest command sequence number for which the client has received a response, and the {@link
 * #eventIndexes()} numbers indicate the highest index for which the client has received an event in
 * proper sequence.
 */
public class KeepAliveRequest extends AbstractRaftRequest {

  private final long[] sessionIds;
  private final long[] commandSequences;
  private final long[] eventIndexes;

  public KeepAliveRequest(
      final long[] sessionIds, final long[] commandSequences, final long[] eventIndexes) {
    this.sessionIds = sessionIds;
    this.commandSequences = commandSequences;
    this.eventIndexes = eventIndexes;
  }

  /**
   * Returns a new keep alive request builder.
   *
   * @return A new keep alive request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the session identifiers.
   *
   * @return The session identifiers.
   */
  public long[] sessionIds() {
    return sessionIds;
  }

  /**
   * Returns the command sequence numbers.
   *
   * @return The command sequence numbers.
   */
  public long[] commandSequenceNumbers() {
    return commandSequences;
  }

  /**
   * Returns the event indexes.
   *
   * @return The event indexes.
   */
  public long[] eventIndexes() {
    return eventIndexes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), sessionIds, commandSequences, eventIndexes);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof KeepAliveRequest) {
      final KeepAliveRequest request = (KeepAliveRequest) object;
      return Arrays.equals(request.sessionIds, sessionIds)
          && Arrays.equals(request.commandSequences, commandSequences)
          && Arrays.equals(request.eventIndexes, eventIndexes);
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("sessionIds", ArraySizeHashPrinter.of(sessionIds))
        .add("commandSequences", ArraySizeHashPrinter.of(commandSequences))
        .add("eventIndexes", ArraySizeHashPrinter.of(eventIndexes))
        .toString();
  }

  /** Keep alive request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, KeepAliveRequest> {

    private long[] sessionIds;
    private long[] commandSequences;
    private long[] eventIndexes;

    /**
     * Sets the session identifiers.
     *
     * @param sessionIds The session identifiers.
     * @return The request builders.
     * @throws NullPointerException if {@code sessionIds} is {@code null}
     */
    public Builder withSessionIds(final long[] sessionIds) {
      this.sessionIds = checkNotNull(sessionIds, "sessionIds cannot be null");
      return this;
    }

    /**
     * Sets the command sequence numbers.
     *
     * @param commandSequences The command sequence numbers.
     * @return The request builder.
     * @throws NullPointerException if {@code commandSequences} is {@code null}
     */
    public Builder withCommandSequences(final long[] commandSequences) {
      this.commandSequences = checkNotNull(commandSequences, "commandSequences cannot be null");
      return this;
    }

    /**
     * Sets the event indexes.
     *
     * @param eventIndexes The event indexes.
     * @return The request builder.
     * @throws NullPointerException if {@code eventIndexes} is {@code null}
     */
    public Builder withEventIndexes(final long[] eventIndexes) {
      this.eventIndexes = checkNotNull(eventIndexes, "eventIndexes cannot be null");
      return this;
    }

    @Override
    public KeepAliveRequest build() {
      validate();
      return new KeepAliveRequest(sessionIds, commandSequences, eventIndexes);
    }

    @Override
    protected void validate() {
      super.validate();
      this.sessionIds = checkNotNull(sessionIds, "sessionIds cannot be null");
      this.commandSequences = checkNotNull(commandSequences, "commandSequences cannot be null");
      this.eventIndexes = checkNotNull(eventIndexes, "eventIndexes cannot be null");
    }
  }
}
