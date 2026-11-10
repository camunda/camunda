/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.mailbox;

import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One mailbox store that the brokers and gateways of a test cluster share, as they would share a
 * database. Tests run in parallel in one JVM, so a member only gets a store if a test registered
 * its member id. Every other cluster in the JVM gets no mailbox.
 *
 * <p>Each member sees the store through a view that counts its calls, so a test can tell which
 * member used the mailbox.
 */
final class SharedMailbox {

  static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(10),
          Duration.ofMillis(100),
          Duration.ofSeconds(30),
          10,
          Duration.ofMinutes(10),
          Duration.ofMinutes(10));

  private static final Map<String, SharedMailbox> BY_MEMBER = new ConcurrentHashMap<>();

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final Map<String, String> brokerZones = new ConcurrentHashMap<>();
  private final Map<String, CountingView> brokerViews = new ConcurrentHashMap<>();
  private final Map<String, CountingView> gatewayViews = new ConcurrentHashMap<>();

  /** Gives the members the store. */
  static SharedMailbox registerFor(final Set<String> memberIds) {
    final var shared = new SharedMailbox();
    memberIds.forEach(id -> BY_MEMBER.put(id, shared));
    return shared;
  }

  static void unregister(final Set<String> memberIds) {
    memberIds.forEach(BY_MEMBER::remove);
  }

  static SharedMailbox of(final String memberId) {
    return BY_MEMBER.get(memberId);
  }

  /** The store itself, to look at what is in it. */
  InMemoryMailboxStore store() {
    return store;
  }

  /**
   * Makes a broker act as if it were in the given zone, instead of its own. The broker then counts
   * the leaders of its real zone as out of reach, so it uses the mailbox for them, also for the
   * partitions that it leads itself.
   */
  void simulateBrokerZone(final String memberId, final String zone) {
    brokerZones.put(memberId, zone);
  }

  /** The zone that the broker acts in, which is {@code ownZone} unless a test chose another. */
  String brokerZone(final String memberId, final String ownZone) {
    return brokerZones.getOrDefault(memberId, ownZone);
  }

  CountingView brokerView(final String memberId) {
    return brokerViews.computeIfAbsent(memberId, id -> new CountingView(store));
  }

  CountingView gatewayView(final String memberId) {
    return gatewayViews.computeIfAbsent(memberId, id -> new CountingView(store));
  }

  /** The store of a member, which counts what the member calls. */
  static final class CountingView {
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final MailboxStore store;

    private CountingView(final MailboxStore target) {
      store =
          (MailboxStore)
              Proxy.newProxyInstance(
                  MailboxStore.class.getClassLoader(),
                  new Class<?>[] {MailboxStore.class},
                  (proxy, method, args) -> {
                    calls
                        .computeIfAbsent(method.getName(), name -> new AtomicInteger())
                        .incrementAndGet();
                    try {
                      return method.invoke(target, args);
                    } catch (final InvocationTargetException e) {
                      throw e.getCause();
                    }
                  });
    }

    MailboxStore store() {
      return store;
    }

    int calls(final String method) {
      final var count = calls.get(method);
      return count == null ? 0 : count.get();
    }
  }
}
