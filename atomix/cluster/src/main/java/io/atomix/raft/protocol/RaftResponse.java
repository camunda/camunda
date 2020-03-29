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

/**
 * Base interface for responses.
 *
 * <p>Each response has a non-null {@link RaftResponse.Status} of either {@link
 * RaftResponse.Status#OK} or {@link RaftResponse.Status#ERROR}. Responses where {@link #status()}
 * is {@link RaftResponse.Status#ERROR} may provide an optional {@link #error()} code.
 */
public interface RaftResponse extends RaftMessage {

  /**
   * Returns the response status.
   *
   * @return The response status.
   */
  Status status();

  /**
   * Returns the response error if the response status is {@code Status.ERROR}
   *
   * @return The response error.
   */
  RaftError error();

  /**
   * Response builder.
   *
   * @param <T> The builder type.
   * @param <U> The response type.
   */
  interface Builder<T extends Builder<T, U>, U extends RaftResponse>
      extends io.atomix.utils.Builder<U> {

    /**
     * Sets the response status.
     *
     * @param status The response status.
     * @return The response builder.
     * @throws NullPointerException if {@code status} is null
     */
    T withStatus(Status status);

    /**
     * Sets the response error.
     *
     * @param type The response error type.
     * @return The response builder.
     * @throws NullPointerException if {@code type} is null
     */
    default T withError(final RaftError.Type type) {
      return withError(new RaftError(type, null));
    }

    /**
     * Sets the response error.
     *
     * @param error The response error.
     * @return The response builder.
     * @throws NullPointerException if {@code error} is null
     */
    T withError(RaftError error);

    /**
     * Sets the response error.
     *
     * @param type The response error type.
     * @param message The response error message.
     * @return The response builder.
     * @throws NullPointerException if {@code type} is null
     */
    default T withError(final RaftError.Type type, final String message) {
      return withError(new RaftError(type, message));
    }
  }

  /** Response status. */
  enum Status {

    /** Indicates a successful response status. */
    OK(1),

    /** Indicates a response containing an error. */
    ERROR(0);

    private final byte id;

    Status(final int id) {
      this.id = (byte) id;
    }

    /**
     * Returns the status for the given identifier.
     *
     * @param id The status identifier.
     * @return The status for the given identifier.
     * @throws IllegalArgumentException if {@code id} is not 0 or 1
     */
    public static Status forId(final int id) {
      switch (id) {
        case 1:
          return OK;
        case 0:
          return ERROR;
        default:
          break;
      }
      throw new IllegalArgumentException("invalid status identifier: " + id);
    }

    /**
     * Returns the status identifier.
     *
     * @return The status identifier.
     */
    public byte id() {
      return id;
    }
  }
}
