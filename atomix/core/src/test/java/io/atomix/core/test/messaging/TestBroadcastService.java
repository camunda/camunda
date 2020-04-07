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
package io.atomix.core.test.messaging;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.cluster.messaging.BroadcastService;
import io.atomix.cluster.messaging.ManagedBroadcastService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Test broadcast service. */
public class TestBroadcastService implements ManagedBroadcastService {
  private final Set<TestBroadcastService> services;
  private final Map<String, Set<Consumer<byte[]>>> listeners = Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();

  public TestBroadcastService(final Set<TestBroadcastService> services) {
    this.services = services;
  }

  @Override
  public void broadcast(final String subject, final byte[] message) {
    services.forEach(
        service -> {
          final Set<Consumer<byte[]>> listeners = service.listeners.get(subject);
          if (listeners != null) {
            listeners.forEach(
                listener -> {
                  listener.accept(message);
                });
          }
        });
  }

  @Override
  public synchronized void addListener(final String subject, final Consumer<byte[]> listener) {
    listeners.computeIfAbsent(subject, s -> Sets.newCopyOnWriteArraySet()).add(listener);
  }

  @Override
  public synchronized void removeListener(final String subject, final Consumer<byte[]> listener) {
    final Set<Consumer<byte[]>> listeners = this.listeners.get(subject);
    if (listeners != null) {
      listeners.remove(listener);
      if (listeners.isEmpty()) {
        this.listeners.remove(subject);
      }
    }
  }

  @Override
  public CompletableFuture<BroadcastService> start() {
    services.add(this);
    started.set(true);
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    services.remove(this);
    started.set(false);
    return CompletableFuture.completedFuture(null);
  }
}
