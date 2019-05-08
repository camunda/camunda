/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.distributedlog.restore;

import java.util.concurrent.CompletableFuture;

public interface RestoreInfoServer extends AutoCloseable {
  /**
   * Start serving {@link RestoreInfoRequest} requests to the given handler.
   *
   * @param server handler to handle incoming requests
   * @return a future which will complete once the server is ready to accept requests
   */
  CompletableFuture<Void> serve(Handler server);

  @Override
  void close();

  @FunctionalInterface
  interface Handler {

    /**
     * Handles a single {@link RestoreInfoRequest} request. The returned response must be non-null.
     *
     * <p>If the returned {@link io.zeebe.distributedlog.restore.RestoreStrategy.ReplicationTarget}
     * is {@link io.zeebe.distributedlog.restore.RestoreStrategy.ReplicationTarget#EVENTS}, then
     * there must exist an event locally at {@link RestoreInfoRequest#getLatestLocalPosition()}. If
     * the returned {@link io.zeebe.distributedlog.restore.RestoreStrategy.ReplicationTarget} is
     * {@link io.zeebe.distributedlog.restore.RestoreStrategy.ReplicationTarget#SNAPSHOT}, then
     * there must exists a snapshot with a position greater than {@link
     * RestoreInfoRequest#getLatestLocalPosition()}.
     *
     * @param request request to handle
     * @return response to return to sender
     */
    RestoreInfoResponse onRestoreInfoRequest(RestoreInfoRequest request);
  }
}
