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

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class CamundaPoolingAsyncClientConnectionManager
    implements AsyncClientConnectionManager, ConnPoolControl<HttpRoute> {
  private final PoolingAsyncClientConnectionManager original;

  public CamundaPoolingAsyncClientConnectionManager(
      final PoolingAsyncClientConnectionManager original) {
    this.original = original;
  }

  @Override
  public Future<AsyncConnectionEndpoint> lease(
      final String id,
      final HttpRoute route,
      final Object state,
      final Timeout requestTimeout,
      final FutureCallback<AsyncConnectionEndpoint> callback) {
    return original.lease(id, route, state, requestTimeout, callback);
  }

  @Override
  public void release(
      final AsyncConnectionEndpoint endpoint, final Object state, final TimeValue keepAlive) {
    CompletableFuture.runAsync(
        () -> {
          original.release(endpoint, state, keepAlive);
        });
  }

  @Override
  public Future<AsyncConnectionEndpoint> connect(
      final AsyncConnectionEndpoint endpoint,
      final ConnectionInitiator connectionInitiator,
      final Timeout connectTimeout,
      final Object attachment,
      final HttpContext context,
      final FutureCallback<AsyncConnectionEndpoint> callback) {
    return original.connect(
        endpoint, connectionInitiator, connectTimeout, attachment, context, callback);
  }

  @Override
  public void upgrade(
      final AsyncConnectionEndpoint endpoint, final Object attachment, final HttpContext context) {
    original.upgrade(endpoint, attachment, context);
  }

  @Override
  public void close(final CloseMode closeMode) {
    original.close(closeMode);
  }

  @Override
  public void close() throws IOException {
    original.close();
  }

  @Override
  public PoolStats getTotalStats() {
    return original.getTotalStats();
  }

  @Override
  public PoolStats getStats(final HttpRoute route) {
    return original.getStats(route);
  }

  @Override
  public void setMaxTotal(final int max) {
    original.setMaxTotal(max);
  }

  @Override
  public int getMaxTotal() {
    return original.getMaxTotal();
  }

  @Override
  public void setDefaultMaxPerRoute(final int max) {
    original.setDefaultMaxPerRoute(max);
  }

  @Override
  public int getDefaultMaxPerRoute() {
    return original.getDefaultMaxPerRoute();
  }

  @Override
  public void setMaxPerRoute(final HttpRoute route, final int max) {
    original.setMaxPerRoute(route, max);
  }

  @Override
  public int getMaxPerRoute(final HttpRoute route) {
    return original.getMaxPerRoute(route);
  }

  @Override
  public void closeIdle(final TimeValue idleTime) {
    original.closeIdle(idleTime);
  }

  @Override
  public void closeExpired() {
    original.closeExpired();
  }

  @Override
  public Set<HttpRoute> getRoutes() {
    return original.getRoutes();
  }
}
