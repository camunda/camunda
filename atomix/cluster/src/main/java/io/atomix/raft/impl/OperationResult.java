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
package io.atomix.raft.impl;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.misc.ArraySizeHashPrinter;

/** Operation result. */
public final class OperationResult {

  private final long index;
  private final long eventIndex;
  private final Throwable error;
  private final byte[] result;

  private OperationResult(
      final long index, final long eventIndex, final Throwable error, final byte[] result) {
    this.index = index;
    this.eventIndex = eventIndex;
    this.error = error;
    this.result = result;
  }

  /**
   * Returns a no-op operation result.
   *
   * @param index the result index
   * @param eventIndex the session's last event index
   * @return the operation result
   */
  public static OperationResult noop(final long index, final long eventIndex) {
    return new OperationResult(index, eventIndex, null, null);
  }

  /**
   * Returns a successful operation result.
   *
   * @param index the result index
   * @param eventIndex the session's last event index
   * @param result the operation result value
   * @return the operation result
   */
  public static OperationResult succeeded(
      final long index, final long eventIndex, final byte[] result) {
    return new OperationResult(index, eventIndex, null, result);
  }

  /**
   * Returns a failed operation result.
   *
   * @param index the result index
   * @param eventIndex the session's last event index
   * @param error the operation error
   * @return the operation result
   */
  public static OperationResult failed(
      final long index, final long eventIndex, final Throwable error) {
    return new OperationResult(index, eventIndex, error, null);
  }

  /**
   * Returns the result index.
   *
   * @return The result index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the result event index.
   *
   * @return The result event index.
   */
  public long eventIndex() {
    return eventIndex;
  }

  /**
   * Returns the operation error.
   *
   * @return the operation error
   */
  public Throwable error() {
    return error;
  }

  /**
   * Returns the result value.
   *
   * @return The result value.
   */
  public byte[] result() {
    return result;
  }

  /**
   * Returns whether the operation failed.
   *
   * @return whether the operation failed
   */
  public boolean failed() {
    return !succeeded();
  }

  /**
   * Returns whether the operation succeeded.
   *
   * @return whether the operation succeeded
   */
  public boolean succeeded() {
    return error == null;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("index", index)
        .add("eventIndex", eventIndex)
        .add("error", error)
        .add("result", ArraySizeHashPrinter.of(result))
        .toString();
  }
}
