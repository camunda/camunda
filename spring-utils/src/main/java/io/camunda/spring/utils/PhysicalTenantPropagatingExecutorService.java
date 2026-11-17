/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Decorates an {@link ExecutorService} so that the physical tenant resolved on the submitting
 * thread is propagated to the worker thread that runs the task.
 *
 * <p>Service-layer reads offload to async executor threads (e.g. {@code
 * CompletableFuture.supplyAsync(..., executor)}), and the authorization check resolves membership
 * lazily on that worker thread, where {@link PhysicalTenantContext#current()} would otherwise find
 * no request scope and throw. This decorator captures the submitting thread's physical tenant
 * <em>by value</em> ({@link PhysicalTenantContext#currentOrNull()}) and binds it on the worker
 * thread for the task's duration. Capturing the value — rather than re-binding the request scope —
 * keeps propagation correct across Spring MVC async dispatch, where the servlet request may already
 * be inactive by the time the task runs.
 *
 * <p>If the task is submitted outside any tenant scope, nothing is bound and the task runs
 * unchanged.
 */
public class PhysicalTenantPropagatingExecutorService implements ExecutorService {

  private final ExecutorService delegate;

  public PhysicalTenantPropagatingExecutorService(final ExecutorService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate executor must not be null");
  }

  private Runnable wrap(final Runnable task) {
    final String physicalTenantId = PhysicalTenantContext.currentOrNull();
    if (physicalTenantId == null) {
      // not submitted within a tenant scope (e.g. a broker-response continuation scheduled off the
      // request thread, which needs no tenant) — nothing to propagate
      return task;
    }
    return () -> {
      final String previous = PhysicalTenantContext.getPropagatedPhysicalTenant();
      PhysicalTenantContext.setPropagatedPhysicalTenant(physicalTenantId);
      try {
        task.run();
      } finally {
        if (previous != null) {
          PhysicalTenantContext.setPropagatedPhysicalTenant(previous);
        } else {
          PhysicalTenantContext.clearPropagatedPhysicalTenant();
        }
      }
    };
  }

  private <T> Callable<T> wrap(final Callable<T> task) {
    final String physicalTenantId = PhysicalTenantContext.currentOrNull();
    if (physicalTenantId == null) {
      // not submitted within a tenant scope (e.g. a broker-response continuation scheduled off the
      // request thread, which needs no tenant) — nothing to propagate
      return task;
    }
    return () -> {
      final String previous = PhysicalTenantContext.getPropagatedPhysicalTenant();
      PhysicalTenantContext.setPropagatedPhysicalTenant(physicalTenantId);
      try {
        return task.call();
      } finally {
        if (previous != null) {
          PhysicalTenantContext.setPropagatedPhysicalTenant(previous);
        } else {
          PhysicalTenantContext.clearPropagatedPhysicalTenant();
        }
      }
    };
  }

  private <T> Collection<? extends Callable<T>> wrapAll(
      final Collection<? extends Callable<T>> tasks) {
    return tasks.stream().map(this::wrap).collect(Collectors.toList());
  }

  @Override
  public void execute(final Runnable command) {
    delegate.execute(wrap(command));
  }

  @Override
  public Future<?> submit(final Runnable task) {
    return delegate.submit(wrap(task));
  }

  @Override
  public <T> Future<T> submit(final Runnable task, final T result) {
    return delegate.submit(wrap(task), result);
  }

  @Override
  public <T> Future<T> submit(final Callable<T> task) {
    return delegate.submit(wrap(task));
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(wrapAll(tasks));
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(wrapAll(tasks), timeout, unit);
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(wrapAll(tasks));
  }

  @Override
  public <T> T invokeAny(
      final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(wrapAll(tasks), timeout, unit);
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit)
      throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }
}
