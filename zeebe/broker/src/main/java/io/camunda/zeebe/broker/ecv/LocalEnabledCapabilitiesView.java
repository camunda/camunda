/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import io.camunda.zeebe.util.ecv.EnabledCapabilitiesView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * The local view of the committed ECV on this broker (design section 8 and open point 14). It is
 * the union of every enabled set that this broker saw: its replica of the system partition, the
 * enabled sets that other members gossip, and the answers of the system partition leader. The view
 * only grows, so the union is safe.
 *
 * <p>The view is kept on disk, so that a restarted broker can refuse itself without the system
 * partition when the view already has a capability that the broker does not support.
 */
public final class LocalEnabledCapabilitiesView implements EnabledCapabilitiesView {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  static final String FILE_NAME = "enabled-capabilities";

  private final Path directory;
  private final Path file;
  private final List<Consumer<CapabilitySet>> listeners = new CopyOnWriteArrayList<>();
  private volatile CapabilitySet enabled;

  public LocalEnabledCapabilitiesView(final Path directory) {
    this.directory = directory;
    file = directory.resolve(FILE_NAME);
    enabled = load(file);
  }

  @Override
  public CapabilitySet enabled() {
    return enabled;
  }

  @Override
  public void addListener(final Consumer<CapabilitySet> listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(final Consumer<CapabilitySet> listener) {
    listeners.remove(listener);
  }

  /** Whether this view was kept on disk by an earlier run of the broker. */
  public boolean isPersisted() {
    return Files.exists(file);
  }

  /**
   * Adds the given set to the view. Returns true if the view grew, after which it is persisted and
   * the listeners are notified.
   */
  public boolean merge(final CapabilitySet committed) {
    final CapabilitySet grown;
    synchronized (this) {
      final var current = enabled;
      grown = current.union(committed);
      if (grown.equals(current) && isPersisted()) {
        return false;
      }
      persist(grown);
      enabled = grown;
    }
    LOG.info("The enabled capabilities on this broker are now {}", grown);
    listeners.forEach(listener -> listener.accept(grown));
    return true;
  }

  private void persist(final CapabilitySet set) {
    try {
      FileUtil.ensureDirectoryExists(directory);
      final var tmp = file.resolveSibling(FILE_NAME + ".tmp");
      Files.writeString(tmp, set.encode(), StandardCharsets.UTF_8);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to persist the enabled capabilities to " + file, e);
    }
  }

  private static CapabilitySet load(final Path file) {
    if (!Files.exists(file)) {
      return CapabilitySet.EMPTY;
    }
    try {
      return CapabilitySet.decode(Files.readString(file, StandardCharsets.UTF_8).trim());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read the enabled capabilities from " + file, e);
    }
  }
}
