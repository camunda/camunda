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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.operation.PrimitiveOperation;

/**
 * Client operation request.
 *
 * <p>Operation requests are sent by clients to servers to execute operations on the replicated
 * state machine. Each operation request must be sequenced with a {@link #sequenceNumber()} number.
 * All operations will be applied to replicated state machines in the sequence in which they were
 * sent by the client. Sequence numbers must always be sequential, and in the event that an
 * operation request fails, it must be resent by the client.
 */
public abstract class OperationRequest extends SessionRequest {

  protected final long sequence;
  protected final PrimitiveOperation operation;

  protected OperationRequest(
      final long session, final long sequence, final PrimitiveOperation operation) {
    super(session);
    this.sequence = sequence;
    this.operation = operation;
  }

  /**
   * Returns the request sequence number.
   *
   * @return The request sequence number.
   */
  public long sequenceNumber() {
    return sequence;
  }

  /**
   * Returns the operation.
   *
   * @return The operation.
   */
  public PrimitiveOperation operation() {
    return operation;
  }

  /** Operation request builder. */
  public abstract static class Builder<T extends Builder<T, U>, U extends OperationRequest>
      extends SessionRequest.Builder<T, U> {

    protected long sequence;
    protected PrimitiveOperation operation;

    /**
     * Sets the request sequence number.
     *
     * @param sequence The request sequence number.
     * @return The request builder.
     * @throws IllegalArgumentException If the request sequence number is not positive.
     */
    @SuppressWarnings("unchecked")
    public T withSequence(final long sequence) {
      checkArgument(sequence >= 0, "sequence must be positive");
      this.sequence = sequence;
      return (T) this;
    }

    /**
     * Sets the request operation.
     *
     * @param operation The operation.
     * @return The request builder.
     * @throws NullPointerException if the request {@code operation} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public T withOperation(final PrimitiveOperation operation) {
      this.operation = checkNotNull(operation, "operation cannot be null");
      return (T) this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(sequence >= 0, "sequence must be positive");
      checkNotNull(operation, "operation cannot be null");
    }
  }
}
