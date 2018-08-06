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
package io.zeebe.gateway.mocks;

import io.zeebe.gateway.api.ZeebeFuture;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.api.commands.TopologyRequestStep1;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TopologyRequestMock implements ZeebeFuture<Topology>, TopologyRequestStep1 {

  private boolean produceError;
  private RuntimeException exception;

  TopologyRequestMock(final boolean produceError) {
    this.exception = new RuntimeException("network error");
    this.produceError = produceError;
  }

  @Override
  public Topology join() {
    try {
      return get();
    } catch (final InterruptedException | ExecutionException e) {
      throw this.exception;
    }
  }

  @Override
  public Topology join(final long timeout, final TimeUnit unit) {
    return join();
  }

  @Override
  public ZeebeFuture<Topology> send() {
    return this;
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public Topology get() throws InterruptedException, ExecutionException {
    if (produceError) {
      throw this.exception;
    }
    return null;
  }

  @Override
  public Topology get(final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return get();
  }
}
