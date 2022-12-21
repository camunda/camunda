/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.utils.net.Address;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** Test unicast service. */
public class TestUnicastService implements ManagedUnicastService {
  private final Address address;
  private final Map<Address, TestUnicastService> services;
  private final Map<String, Map<BiConsumer<Address, byte[]>, Executor>> listeners =
      Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();
  private final Set<Address> partitions = Sets.newConcurrentHashSet();

  public TestUnicastService(
      final Address address, final Map<Address, TestUnicastService> services) {
    this.address = address;
    this.services = services;
  }

  /**
   * Returns the service address.
   *
   * @return the service address
   */
  Address address() {
    return address;
  }

  /** Partitions the node from the given address. */
  void partition(final Address address) {
    partitions.add(address);
  }

  /** Heals the partition from the given address. */
  void heal(final Address address) {
    partitions.remove(address);
  }

  /**
   * Returns a boolean indicating whether this node is partitioned from the given address.
   *
   * @param address the address to check
   * @return whether this node is partitioned from the given address
   */
  boolean isPartitioned(final Address address) {
    return partitions.contains(address);
  }

  @Override
  public void unicast(final Address address, final String subject, final byte[] message) {
    if (isPartitioned(address)) {
      return;
    }

    final TestUnicastService service = services.get(address);
    if (service != null) {
      final Map<BiConsumer<Address, byte[]>, Executor> listeners = service.listeners.get(subject);
      if (listeners != null) {
        listeners.forEach(
            (listener, executor) -> executor.execute(() -> listener.accept(this.address, message)));
      }
    }
  }

  @Override
  public synchronized void addListener(
      final String subject, final BiConsumer<Address, byte[]> listener, final Executor executor) {
    listeners.computeIfAbsent(subject, s -> Maps.newConcurrentMap()).put(listener, executor);
  }

  @Override
  public synchronized void removeListener(
      final String subject, final BiConsumer<Address, byte[]> listener) {
    final Map<BiConsumer<Address, byte[]>, Executor> listeners = this.listeners.get(subject);
    if (listeners != null) {
      listeners.remove(listener);
      if (listeners.isEmpty()) {
        this.listeners.remove(subject);
      }
    }
  }

  @Override
  public CompletableFuture<UnicastService> start() {
    services.put(address, this);
    started.set(true);
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    services.remove(address);
    started.set(false);
    return CompletableFuture.completedFuture(null);
  }
}
