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
package io.camunda.zeebe.client.impl.http;

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClientException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implements a {@link ZeebeFuture} representing a HTTP call. Supports propagating cancellation of
 * the top level future to the underlying transport future.
 *
 * @param <RespT> the expected response type
 */
public class HttpZeebeFuture<RespT> extends CompletableFuture<RespT> implements ZeebeFuture<RespT> {

  private volatile Future<?> transportFuture;

  @Override
  public RespT join(final long timeout, final TimeUnit unit) {
    try {
      return super.get(timeout, unit);
    } catch (final ExecutionException e) {
      throw new ClientException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ClientException("Failed: interrupted while awaiting response", e);
    } catch (final TimeoutException e) {
      throw new ClientException("Failed: timed out waiting on client response", e);
    }
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
    if (transportFuture != null) {
      transportFuture.cancel(mayInterruptIfRunning);
    }

    return super.cancel(mayInterruptIfRunning);
  }

  public void transportFuture(final Future<?> httpFuture) {
    this.transportFuture = httpFuture;

    // possibly we were already cancelled between calls
    if (isCancelled()) {
      httpFuture.cancel(true);
    }
  }
}
