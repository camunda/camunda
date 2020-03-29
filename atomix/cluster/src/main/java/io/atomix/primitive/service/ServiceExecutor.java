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

package io.atomix.primitive.service;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.Consistency;
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.utils.concurrent.Scheduler;
import io.atomix.utils.time.WallClockTimestamp;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Facilitates registration and execution of state machine commands and provides deterministic
 * scheduling.
 *
 * <p>The state machine executor is responsible for managing input to and output from a {@link
 * PrimitiveService}. As operations are committed to the Raft log, the executor is responsible for
 * applying them to the state machine. {@link OperationType#COMMAND commands} are guaranteed to be
 * applied to the state machine in the order in which they appear in the Raft log and always in the
 * same thread, so state machines don't have to be thread safe. {@link OperationType#QUERY queries}
 * are not generally written to the Raft log and will instead be applied according to their {@link
 * Consistency}.
 *
 * <p>State machines can use the executor to provide deterministic scheduling during the execution
 * of command callbacks.
 *
 * <pre>{@code
 * private Object putWithTtl(Commit<PutWithTtl> commit) {
 *   map.put(commit.operation().key(), commit);
 *   executor.schedule(Duration.ofMillis(commit.operation().ttl()), () -> {
 *     map.remove(commit.operation().key()).close();
 *   });
 * }
 *
 * }</pre>
 *
 * As with all state machine callbacks, the executor will ensure scheduled callbacks are executed
 * sequentially and deterministically. As long as state machines schedule callbacks
 * deterministically, callbacks will be executed deterministically. Internally, the state machine
 * executor triggers callbacks based on various timestamps in the Raft log. This means the scheduler
 * is dependent on internal or user-defined operations being written to the log. Prior to the
 * execution of a command, any expired scheduled callbacks will be executed based on the command's
 * logged timestamp.
 *
 * <p>It's important to note that callbacks can only be scheduled during {@link PrimitiveOperation}
 * operations or by recursive scheduling. If a state machine attempts to schedule a callback via the
 * executor during the execution of a query, a {@link IllegalStateException} will be thrown. This is
 * because queries are usually only applied on a single state machine within the cluster, and so
 * scheduling callbacks in reaction to query execution would not be deterministic.
 *
 * @see PrimitiveService
 * @see ServiceContext
 */
public interface ServiceExecutor extends Executor, Scheduler {

  /**
   * Increments the service clock.
   *
   * @param timestamp the wall clock timestamp
   */
  void tick(WallClockTimestamp timestamp);

  /**
   * Applies the given commit to the executor.
   *
   * @param commit the commit to apply
   * @return the commit result
   */
  byte[] apply(Commit<byte[]> commit);

  /**
   * Registers a operation callback.
   *
   * @param operationId the operation identifier
   * @param callback the operation callback
   * @throws NullPointerException if the {@code operationId} or {@code callback} is null
   */
  void handle(OperationId operationId, Function<Commit<byte[]>, byte[]> callback);

  /**
   * Registers a operation callback.
   *
   * @param operationId the operation identifier
   * @param callback the operation callback
   * @throws NullPointerException if the {@code operationId} or {@code callback} is null
   */
  default void register(final OperationId operationId, final Runnable callback) {
    checkNotNull(operationId, "operationId cannot be null");
    checkNotNull(callback, "callback cannot be null");
    handle(
        operationId,
        commit -> {
          callback.run();
          return null;
        });
  }

  /**
   * Registers a no argument operation callback.
   *
   * @param operationId the operation identifier
   * @param callback the operation callback
   * @throws NullPointerException if the {@code operationId} or {@code callback} is null
   */
  <R> void register(OperationId operationId, Supplier<R> callback);

  /**
   * Registers a operation callback.
   *
   * @param operationId the operation identifier
   * @param callback the operation callback
   * @throws NullPointerException if the {@code operationId} or {@code callback} is null
   */
  <T> void register(OperationId operationId, Consumer<Commit<T>> callback);

  /**
   * Registers an operation callback.
   *
   * @param operationId the operation identifier
   * @param callback the operation callback
   * @throws NullPointerException if the {@code operationId} or {@code callback} is null
   */
  <T, R> void register(OperationId operationId, Function<Commit<T>, R> callback);
}
