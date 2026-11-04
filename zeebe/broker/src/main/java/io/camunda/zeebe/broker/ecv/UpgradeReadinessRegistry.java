/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * The upgrade readiness of the components of this broker (design section 2). A component that
 * registered and did not report yet is not ready. The readiness resets when the broker restarts.
 */
public final class UpgradeReadinessRegistry {
  private final SortedMap<String, ComponentReadiness> components = new ConcurrentSkipListMap<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  /** Registers a component, which is not ready until it reports that it is. */
  public Component register(final String name) {
    final var component = new Component(name);
    component.notReady("did not report yet");
    return component;
  }

  public Map<String, ComponentReadiness> snapshot() {
    return Map.copyOf(components);
  }

  void addListener(final Runnable listener) {
    listeners.add(listener);
  }

  void removeListener(final Runnable listener) {
    listeners.remove(listener);
  }

  private void update(final String name, final @Nullable ComponentReadiness readiness) {
    if (readiness == null) {
      components.remove(name);
    } else {
      components.put(name, readiness);
    }
    listeners.forEach(Runnable::run);
  }

  public record ComponentReadiness(boolean ready, @Nullable String reason) {}

  /** The handle of a registered component, to report its readiness. Thread-safe. */
  public final class Component {
    private final String name;

    private Component(final String name) {
      this.name = name;
    }

    public void ready() {
      update(name, new ComponentReadiness(true, null));
    }

    public void notReady(final String reason) {
      update(name, new ComponentReadiness(false, reason));
    }

    public void unregister() {
      update(name, null);
    }
  }
}
