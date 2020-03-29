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

import io.atomix.raft.RaftError;
import io.atomix.utils.misc.ArraySizeHashPrinter;
import java.util.Arrays;
import java.util.Objects;

/**
 * Base client operation response.
 *
 * <p>All operation responses are sent with a {@link #result()} and the {@link #index()} (or index)
 * of the state machine at the point at which the operation was evaluated. The version allows
 * clients to ensure state progresses monotonically when switching servers by providing the state
 * machine version in future operation requests.
 */
public abstract class OperationResponse extends SessionResponse {

  protected final long index;
  protected final long eventIndex;
  protected final byte[] result;
  protected final long lastSequence;

  public OperationResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long eventIndex,
      final byte[] result,
      final long lastSequence) {
    super(status, error);
    this.index = index;
    this.eventIndex = eventIndex;
    this.result = result;
    this.lastSequence = lastSequence;
  }

  /**
   * Returns the operation index.
   *
   * @return The operation index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the event index.
   *
   * @return The event index.
   */
  public long eventIndex() {
    return eventIndex;
  }

  /**
   * Returns the operation result.
   *
   * @return The operation result.
   */
  public byte[] result() {
    return result;
  }

  /**
   * Returns the last in sequence command.
   *
   * <p>This argument is only populated if the command request failed.
   *
   * @return The last command sequence number.
   */
  public long lastSequenceNumber() {
    return lastSequence;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), status, index, eventIndex, lastSequence, result);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }

    final OperationResponse response = (OperationResponse) object;
    return response.status == status
        && Objects.equals(response.error, error)
        && response.index == index
        && response.eventIndex == eventIndex
        && response.lastSequence == lastSequence
        && Arrays.equals(response.result, result);
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("index", index)
          .add("eventIndex", eventIndex)
          .add("result", result != null ? ArraySizeHashPrinter.of(result) : null)
          .toString();
    } else {
      return toStringHelper(this)
          .add("status", status)
          .add("error", error)
          .add("lastSequence", lastSequence)
          .toString();
    }
  }

  /** Operation response builder. */
  public abstract static class Builder<T extends Builder<T, U>, U extends OperationResponse>
      extends SessionResponse.Builder<T, U> {

    protected long index;
    protected long eventIndex;
    protected byte[] result;
    protected long lastSequence;

    /**
     * Sets the response index.
     *
     * @param index The response index.
     * @return The response builder.
     * @throws IllegalArgumentException If the response index is not positive.
     */
    @SuppressWarnings("unchecked")
    public T withIndex(final long index) {
      checkArgument(index >= 0, "index must be positive");
      this.index = index;
      return (T) this;
    }

    /**
     * Sets the response index.
     *
     * @param eventIndex The response event index.
     * @return The response builder.
     * @throws IllegalArgumentException If the response index is not positive.
     */
    @SuppressWarnings("unchecked")
    public T withEventIndex(final long eventIndex) {
      checkArgument(eventIndex >= 0, "eventIndex must be positive");
      this.eventIndex = eventIndex;
      return (T) this;
    }

    /**
     * Sets the operation response result.
     *
     * @param result The response result.
     * @return The response builder.
     * @throws NullPointerException if {@code result} is null
     */
    @SuppressWarnings("unchecked")
    public T withResult(final byte[] result) {
      this.result = result;
      return (T) this;
    }

    /**
     * Sets the last sequence number.
     *
     * @param lastSequence The last sequence number.
     * @return The command response builder.
     */
    @SuppressWarnings("unchecked")
    public T withLastSequence(final long lastSequence) {
      checkArgument(lastSequence >= 0, "lastSequence must be positive");
      this.lastSequence = lastSequence;
      return (T) this;
    }

    @Override
    protected void validate() {
      super.validate();
      if (status == Status.OK) {
        checkArgument(index >= 0, "index must be positive");
        checkArgument(eventIndex >= 0, "eventIndex must be positive");
        checkArgument(lastSequence >= 0, "lastSequence must be positive");
      }
    }
  }
}
