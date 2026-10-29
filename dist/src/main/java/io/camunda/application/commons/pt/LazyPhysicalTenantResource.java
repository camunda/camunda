/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resource of one physical tenant that is created on its first use rather than when the node
 * starts, so that a node only pays for the tenants it serves.
 *
 * <p>Concurrent first uses create it once. A failed creation is reported to the caller and to
 * {@link #state()}, and is not retried before the backoff has passed, so a tenant that cannot be
 * served fails its own requests quickly without slowing down any other tenant.
 */
@NullMarked
public final class LazyPhysicalTenantResource<T> implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(LazyPhysicalTenantResource.class);

  private final String physicalTenantId;
  private final String name;
  private final Supplier<T> factory;
  private final Consumer<T> closer;
  private final Duration retryBackoff;
  private final InstantSource clock;
  private final ReentrantLock lock = new ReentrantLock();

  private volatile @Nullable T resource;
  private volatile State state = State.NOT_CREATED;
  private @Nullable RuntimeException lastFailure;
  private Instant lastFailureAt = Instant.MIN;

  public LazyPhysicalTenantResource(
      final String physicalTenantId,
      final String name,
      final Supplier<T> factory,
      final Consumer<T> closer,
      final Duration retryBackoff,
      final InstantSource clock) {
    this.physicalTenantId = physicalTenantId;
    this.name = name;
    this.factory = factory;
    this.closer = closer;
    this.retryBackoff = retryBackoff;
    this.clock = clock;
  }

  /**
   * Returns the resource, creating it if this is its first use.
   *
   * @throws PhysicalTenantResourceUnavailableException if it cannot be created, or its last
   *     creation failed less than the backoff ago
   * @throws IllegalStateException if it has been closed
   */
  public T get() {
    final var created = resource;
    if (created != null) {
      return created;
    }
    lock.lock();
    try {
      return createIfAbsent();
    } finally {
      lock.unlock();
    }
  }

  public Optional<T> getIfCreated() {
    return Optional.ofNullable(resource);
  }

  public State state() {
    return state;
  }

  public @Nullable Throwable lastFailure() {
    return lastFailure;
  }

  public String physicalTenantId() {
    return physicalTenantId;
  }

  @Override
  public void close() {
    lock.lock();
    try {
      final var created = resource;
      resource = null;
      state = State.CLOSED;
      if (created != null) {
        closer.accept(created);
      }
    } finally {
      lock.unlock();
    }
  }

  private T createIfAbsent() {
    final var created = resource;
    if (created != null) {
      return created;
    }
    if (state == State.CLOSED) {
      throw new IllegalStateException(
          "The %s of physical tenant '%s' are closed".formatted(name, physicalTenantId));
    }
    if (state == State.FAILED && clock.instant().isBefore(lastFailureAt.plus(retryBackoff))) {
      throw unavailable(Objects.requireNonNull(lastFailure));
    }

    final long startedAt = System.nanoTime();
    try {
      final var fresh = Objects.requireNonNull(factory.get(), "factory returned null");
      resource = fresh;
      state = State.CREATED;
      lastFailure = null;
      LOG.info(
          "Created the {} of physical tenant '{}' on first use in {} ms",
          name,
          physicalTenantId,
          Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
      return fresh;
    } catch (final RuntimeException e) {
      lastFailure = e;
      lastFailureAt = clock.instant();
      state = State.FAILED;
      LOG.warn("Failed to create the {} of physical tenant '{}'", name, physicalTenantId, e);
      throw unavailable(e);
    }
  }

  private PhysicalTenantResourceUnavailableException unavailable(final RuntimeException cause) {
    return new PhysicalTenantResourceUnavailableException(physicalTenantId, name, cause);
  }

  public enum State {
    /** Nothing has used the resource yet, so this node holds nothing for it. */
    NOT_CREATED,
    CREATED,
    /** The last creation failed; the next use after the backoff tries again. */
    FAILED,
    CLOSED
  }
}
