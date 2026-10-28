/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds a client built from connection settings, and replaces it when the settings change: build
 * the new client, verify it, swap it in, then drain and close the old one. A client that fails to
 * build or to verify is closed and the old one stays in use, so a failed refresh never takes away a
 * working client.
 *
 * <p>Callers read {@link #client()} on every use rather than keeping the instance, so that a swap
 * reaches them.
 *
 * @param <S> the resolved connection settings, compared with {@code equals}
 * @param <C> the client
 */
public final class RefreshableClient<S, C> implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(RefreshableClient.class);

  private final String name;
  private final Lifecycle<S, C> lifecycle;
  private volatile Current<S, C> current;
  private boolean closed;

  private RefreshableClient(
      final String name, final Lifecycle<S, C> lifecycle, final Current<S, C> current) {
    this.name = name;
    this.lifecycle = lifecycle;
    this.current = current;
  }

  /** Builds the first client without verifying it, so that startup does not need the service. */
  public static <S, C> RefreshableClient<S, C> of(
      final String name, final S settings, final Lifecycle<S, C> lifecycle) {
    return new RefreshableClient<>(
        name, lifecycle, new Current<>(settings, lifecycle.build(settings)));
  }

  public C client() {
    return current.client();
  }

  public S settings() {
    return current.settings();
  }

  /**
   * Applies new settings. Does nothing if they equal the current ones.
   *
   * @throws Exception if the new client cannot be built or verified; the current client stays
   */
  public synchronized void refresh(final S settings) throws Exception {
    if (closed) {
      throw new IllegalStateException("Client '" + name + "' is closed");
    }
    final var previous = current;
    if (previous.settings().equals(settings)) {
      return;
    }
    if (lifecycle.updateInPlace(previous.client(), previous.settings(), settings)) {
      current = new Current<>(settings, previous.client());
      LOG.info("Updated client '{}' in place", name);
      return;
    }

    final var candidate = lifecycle.build(settings);
    try {
      lifecycle.verify(candidate);
    } catch (final Exception e) {
      closeQuietly(candidate);
      throw e;
    }
    current = new Current<>(settings, candidate);
    LOG.info("Replaced client '{}', draining the previous one", name);
    try {
      lifecycle.drain(previous.client());
    } finally {
      closeQuietly(previous.client());
    }
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      closeQuietly(current.client());
    }
  }

  private void closeQuietly(final C client) {
    try {
      lifecycle.close(client);
    } catch (final RuntimeException e) {
      LOG.warn("Failed to close a client of '{}'", name, e);
    }
  }

  /** How a client is built, checked, updated, drained and closed. */
  public interface Lifecycle<S, C> {

    C build(S settings);

    /** Proves that the client can serve, for example by opening one connection. */
    void verify(C client) throws Exception;

    /**
     * Applies the new settings to the running client if it supports that, for example new
     * credentials to a pool that uses them for its next connection. Must verify the new settings
     * before it applies them.
     *
     * @return whether the client was updated; if not, a new client is built instead
     */
    default boolean updateInPlace(final C client, final S current, final S next) throws Exception {
      return false;
    }

    /** Waits, within a bound, until the client no longer serves a request in flight. */
    default void drain(final C client) {}

    void close(C client);
  }

  private record Current<S, C>(S settings, C client) {}
}
