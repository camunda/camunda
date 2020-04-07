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

import io.atomix.raft.protocol.CommandRequest;
import io.atomix.raft.protocol.CommandResponse;
import java.util.concurrent.CompletableFuture;

/** Pending command. */
public final class PendingCommand {

  private final CommandRequest request;
  private final CompletableFuture<CommandResponse> future;

  public PendingCommand(
      final CommandRequest request, final CompletableFuture<CommandResponse> future) {
    this.request = request;
    this.future = future;
  }

  /**
   * Returns the pending command request.
   *
   * @return the pending command request
   */
  public CommandRequest request() {
    return request;
  }

  /**
   * Returns the pending command future.
   *
   * @return the pending command future
   */
  public CompletableFuture<CommandResponse> future() {
    return future;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("request", request).toString();
  }
}
