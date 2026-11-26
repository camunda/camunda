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
package io.camunda.client.impl.backpressure;

import io.camunda.client.api.command.ClientException;

/**
 * Thrown when so many requests wait for the concurrency limit that the client rejects further ones
 * instead of queueing them, to keep its memory use bounded.
 */
public final class BackpressureQueueFullException extends ClientException {

  private static final long serialVersionUID = 1L;

  BackpressureQueueFullException(final int maxWaiters) {
    super(
        String.format(
            "Expected to queue the request until the cluster recovers from backpressure, but "
                + "%d requests are already waiting. Rejecting the request to prevent unbounded "
                + "memory growth",
            maxWaiters));
  }
}
