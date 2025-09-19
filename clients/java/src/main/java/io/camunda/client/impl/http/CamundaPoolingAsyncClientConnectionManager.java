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
package io.camunda.client.impl.http;

import java.util.concurrent.CompletableFuture;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;

/**
 * {@code PoolingAsyncClientConnectionManager} maintains a pool of non-blocking {@link
 * org.apache.hc.core5.http.HttpConnection}s and is able to service connection requests from
 * multiple execution threads. Connections are pooled on a per route basis. A request for a route
 * which already the manager has persistent connections for available in the pool will be services
 * by leasing a connection from the pool rather than creating a new connection.
 *
 * <p>{@code PoolingAsyncClientConnectionManager} maintains a maximum limit of connection on a per
 * route basis and in total. Connection limits can be adjusted using {@link ConnPoolControl}
 * methods.
 *
 * <p>Total time to live (TTL) set at construction time defines maximum life span of persistent
 * connections regardless of their expiration setting. No persistent connection will be re-used past
 * its TTL value.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class CamundaPoolingAsyncClientConnectionManager
    extends PoolingAsyncClientConnectionManager {

  public CamundaPoolingAsyncClientConnectionManager(
      final AsyncClientConnectionOperator connectionOperator,
      final PoolConcurrencyPolicy poolConcurrencyPolicy,
      final PoolReusePolicy poolReusePolicy,
      final TimeValue timeToLive,
      final boolean messageMultiplexing) {
    super(
        connectionOperator,
        poolConcurrencyPolicy,
        poolReusePolicy,
        timeToLive,
        messageMultiplexing);
  }

  @Override
  public void release(
      final AsyncConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
    CompletableFuture.runAsync(
        () -> {
          super.release(endpoint, state, keepAlive);
        });
  }
}
