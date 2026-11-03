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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
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
 *
 * <p>A resource that nothing has used for a while can be {@link #releaseIfIdle released}: it is
 * closed and created again on its next use. Work done through {@link #use} or {@link #acquire}
 * holds it, so it is never closed under a caller.
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
  private final AtomicInteger inUse = new AtomicInteger();

  private volatile @Nullable T resource;
  private volatile Instant lastUsedAt = Instant.MIN;
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

  /** Runs the work on the resource, creating it if needed, and holds it until the work returns. */
  public <R> R use(final Function<? super T, R> work) {
    try (final var lease = acquire()) {
      return work.apply(lease.resource());
    }
  }

  /**
   * Holds the resource, creating it if needed, until the lease is closed, for work that outlives
   * one call, such as a database session.
   */
  public Lease<T> acquire() {
    inUse.incrementAndGet();
    try {
      final var held = get();
      lastUsedAt = clock.instant();
      return new Lease<>(this, held, true);
    } catch (final RuntimeException e) {
      inUse.decrementAndGet();
      throw e;
    }
  }

  /**
   * Holds the resource if it exists, without creating it and without counting as a use, for
   * background work such as metrics that must not keep an otherwise idle tenant alive.
   */
  public Optional<Lease<T>> acquireIfCreated() {
    inUse.incrementAndGet();
    final var held = resource;
    if (held == null) {
      inUse.decrementAndGet();
      return Optional.empty();
    }
    return Optional.of(new Lease<>(this, held, false));
  }

  /**
   * Closes the resource if nothing holds it and nothing has used it for the given time. The next
   * use creates it again.
   *
   * @return whether it was released
   */
  public boolean releaseIfIdle(final Duration idleFor) {
    if (resource == null || inUse.get() > 0) {
      return false;
    }
    lock.lock();
    try {
      final var created = resource;
      if (created == null || clock.instant().isBefore(lastUsedAt.plus(idleFor))) {
        return false;
      }
      // a use that starts now either sees the resource gone and waits for the lock to create it
      // again, or has already counted itself, which this check sees
      resource = null;
      if (inUse.get() > 0) {
        resource = created;
        return false;
      }
      state = State.NOT_CREATED;
      closer.accept(created);
      LOG.info(
          "Released the {} of physical tenant '{}' after {} without use",
          name,
          physicalTenantId,
          idleFor);
      return true;
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
      lastUsedAt = clock.instant();
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

  /** A hold on the resource; closing it lets the resource be released again. */
  public static final class Lease<T> implements AutoCloseable {
    private final LazyPhysicalTenantResource<T> owner;
    private final T resource;
    private final boolean countsAsUse;
    private boolean closed;

    private Lease(
        final LazyPhysicalTenantResource<T> owner, final T resource, final boolean countsAsUse) {
      this.owner = owner;
      this.resource = resource;
      this.countsAsUse = countsAsUse;
    }

    public T resource() {
      return resource;
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        if (countsAsUse) {
          owner.lastUsedAt = owner.clock.instant();
        }
        owner.inUse.decrementAndGet();
      }
    }
  }

  public enum State {
    /** Nothing has used the resource yet, or it was released, so this node holds nothing for it. */
    NOT_CREATED,
    CREATED,
    /** The last creation failed; the next use after the backoff tries again. */
    FAILED,
    CLOSED
  }
}
