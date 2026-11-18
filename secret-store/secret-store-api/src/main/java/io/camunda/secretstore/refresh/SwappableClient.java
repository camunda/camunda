/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.refresh;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes new values by building a new client next to the current one:
 *
 * <ol>
 *   <li>build a new client from the new values, and check it against the real service, within the
 *       build timeout;
 *   <li>send new work to the new client;
 *   <li>close the old client once its running work has finished, or after the drain timeout.
 * </ol>
 *
 * If the build or the check fails, the new client is closed and the current one stays in use.
 *
 * <p>Work is sent to a client by taking a {@link #lease()}, and the client isn't closed while a
 * lease on it is open, up to the drain timeout.
 *
 * @param <C> the type of client
 */
public abstract class SwappableClient<C extends AutoCloseable>
    implements RefreshableClient, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SwappableClient.class);
  private static final Executor EXECUTOR =
      task -> Thread.ofVirtual().name("swappable-client").start(task);

  private final String name;
  private final Set<String> properties;
  private final Duration buildTimeout;
  private final Duration drainTimeout;
  private volatile Generation<C> current;

  protected SwappableClient(
      final String name,
      final Set<String> properties,
      final C client,
      final Duration buildTimeout,
      final Duration drainTimeout) {
    this.name = name;
    this.properties = Set.copyOf(properties);
    this.buildTimeout = buildTimeout;
    this.drainTimeout = drainTimeout;
    current = new Generation<>(client);
  }

  /** Builds a new client from the given values. */
  protected abstract C build(Map<String, String> values) throws Exception;

  /**
   * Checks the client against the real service, and throws if it can't be used: a {@link
   * ClientCheckException} where it can tell whether the service rejected the credentials or
   * couldn't be reached.
   */
  protected abstract void check(C client) throws Exception;

  @Override
  public String name() {
    return name;
  }

  @Override
  public Set<String> properties() {
    return properties;
  }

  /** Opens a lease on the current client, to send work to it. */
  public Lease<C> lease() {
    while (true) {
      final var generation = current;
      generation.leases.incrementAndGet();
      if (generation == current) {
        return new Lease<>(generation);
      }
      generation.release();
    }
  }

  @Override
  public final void refresh(final Map<String, String> values) {
    final var next = buildAndCheck(values);
    final var previous = current;
    current = new Generation<>(next);
    previous.retire(drainTimeout);
  }

  @Override
  public void close() {
    current.retire(Duration.ZERO);
  }

  private C buildAndCheck(final Map<String, String> values) {
    final var built = new CompletableFuture<C>();
    EXECUTOR.execute(
        () -> {
          try {
            final var client = build(values);
            try {
              check(client);
            } catch (final Throwable e) {
              closeQuietly(client);
              throw e;
            }
            if (!built.complete(client)) {
              // the build timed out while the client was checked
              closeQuietly(client);
            }
          } catch (final Throwable e) {
            built.completeExceptionally(e);
          }
        });
    try {
      return built.get(buildTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      if (!built.completeExceptionally(e)) {
        built.thenAccept(SwappableClient::closeQuietly);
      }
      throw new ClientRefreshException(
          "Failed to build a new %s client within %s".formatted(name, buildTimeout), e);
    } catch (final ExecutionException e) {
      throw new ClientRefreshException(
          "Failed to build a new %s client".formatted(name), e.getCause());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      built.completeExceptionally(e);
      built.thenAccept(SwappableClient::closeQuietly);
      throw new ClientRefreshException(
          "Interrupted while building a new %s client".formatted(name), e);
    }
  }

  private static void closeQuietly(final AutoCloseable client) {
    try {
      client.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close a client", e);
    }
  }

  /** A client in use, which mustn't be closed until the lease is. */
  public static final class Lease<C extends AutoCloseable> implements AutoCloseable {

    private final Generation<C> generation;
    private boolean closed;

    private Lease(final Generation<C> generation) {
      this.generation = generation;
    }

    public C client() {
      return generation.client;
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        generation.release();
      }
    }
  }

  private static final class Generation<C extends AutoCloseable> {

    private final C client;
    private final AtomicInteger leases = new AtomicInteger();
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private volatile boolean retired;

    private Generation(final C client) {
      this.client = Objects.requireNonNull(client);
    }

    private void retire(final Duration drainTimeout) {
      retired = true;
      drained
          .completeOnTimeout(null, drainTimeout.toMillis(), TimeUnit.MILLISECONDS)
          .thenRunAsync(() -> closeQuietly(client), EXECUTOR);
      if (leases.get() == 0) {
        drained.complete(null);
      }
    }

    private void release() {
      if (leases.decrementAndGet() == 0 && retired) {
        drained.complete(null);
      }
    }
  }
}
