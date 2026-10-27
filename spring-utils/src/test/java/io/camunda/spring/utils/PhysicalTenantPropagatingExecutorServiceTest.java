/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PhysicalTenantPropagatingExecutorServiceTest {

  private final ExecutorService delegate = Executors.newSingleThreadExecutor();
  private final PhysicalTenantPropagatingExecutorService executor =
      new PhysicalTenantPropagatingExecutorService(delegate);

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
    // guarantee the propagation ThreadLocal never bleeds into a later test, even on failure
    PhysicalTenantContext.clearPropagatedPhysicalTenant();
    delegate.shutdownNow();
  }

  private void bindRequestWithPhysicalTenant(final String physicalTenantId) {
    final HttpServletRequest request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  void shouldPropagatePhysicalTenantToWorkerThread() throws Exception {
    // given a request scope bound on the submitting thread
    bindRequestWithPhysicalTenant("blue");

    // when a task runs on a worker thread
    final String observed = executor.submit(PhysicalTenantContext::current).get();

    // then the worker observed the submitting thread's physical tenant
    assertThat(observed).isEqualTo("blue");
  }

  @Test
  void shouldNotThrowWhenSubmittedOutsideRequestScope() throws Exception {
    // given no request scope is bound

    // when a task is submitted without a request scope
    final String result =
        executor
            .submit(
                () -> {
                  // nothing is bound, so current() still fails fast on the worker thread
                  assertThatThrownBy(PhysicalTenantContext::current)
                      .isInstanceOf(IllegalStateException.class);
                  return "ok";
                })
            .get();

    // then the task completes normally
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void shouldRestoreOuterPropagatedTenantAfterNestedInlineTask() {
    // given an outer worker thread that already has a propagated tenant bound
    PhysicalTenantContext.setPropagatedPhysicalTenant("outer");

    // when an inline (same-thread) executor causes the nested task to run inline, simulating
    // CallerRunsPolicy: the inner task propagates a different tenant onto the same thread
    final PhysicalTenantPropagatingExecutorService inlineExecutor =
        new PhysicalTenantPropagatingExecutorService(newCallerRunsInlineExecutor());

    // simulate submitting the inner task from a request scope so wrap() captures a tenant
    bindRequestWithPhysicalTenant("inner");
    inlineExecutor.execute(
        () -> assertThat(PhysicalTenantContext.getPropagatedPhysicalTenant()).isEqualTo("inner"));
    RequestContextHolder.resetRequestAttributes();

    // then the outer propagated tenant is restored on the current thread
    assertThat(PhysicalTenantContext.getPropagatedPhysicalTenant()).isEqualTo("outer");
  }

  @Test
  void shouldRestoreOuterPropagatedTenantAfterNestedInlineCallable() throws Exception {
    // given an outer propagated tenant on the current thread
    PhysicalTenantContext.setPropagatedPhysicalTenant("outer-callable");

    // when an inline executor runs a Callable that binds a nested tenant on the same thread
    final PhysicalTenantPropagatingExecutorService inlineExecutor =
        new PhysicalTenantPropagatingExecutorService(newCallerRunsInlineExecutor());

    bindRequestWithPhysicalTenant("inner-callable");
    final String observedInner =
        inlineExecutor.submit(() -> PhysicalTenantContext.getPropagatedPhysicalTenant()).get();
    RequestContextHolder.resetRequestAttributes();

    // then the inner task saw its tenant and the outer is restored afterwards
    assertThat(observedInner).isEqualTo("inner-callable");
    assertThat(PhysicalTenantContext.getPropagatedPhysicalTenant()).isEqualTo("outer-callable");
  }

  /** Returns an {@link ExecutorService} that runs every submitted task on the calling thread. */
  private static ExecutorService newCallerRunsInlineExecutor() {
    return new AbstractExecutorService() {
      private boolean shutdown;

      @Override
      public void execute(final Runnable command) {
        command.run();
      }

      @Override
      public void shutdown() {
        shutdown = true;
      }

      @Override
      public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
      }

      @Override
      public boolean isShutdown() {
        return shutdown;
      }

      @Override
      public boolean isTerminated() {
        return shutdown;
      }

      @Override
      public boolean awaitTermination(final long timeout, final TimeUnit unit) {
        return true;
      }
    };
  }

  @Test
  void shouldNotLeakPropagatedPhysicalTenantToLaterUnboundTask() throws Exception {
    // given a propagated task ran on the (single) worker thread
    bindRequestWithPhysicalTenant("blue");
    assertThat(executor.submit(PhysicalTenantContext::current).get()).isEqualTo("blue");

    // when a later task is submitted with no request scope, reusing the same worker thread
    RequestContextHolder.resetRequestAttributes();
    final boolean resolvedPhysicalTenant =
        executor
            .submit(
                () -> {
                  try {
                    PhysicalTenantContext.current();
                    return true;
                  } catch (final IllegalStateException e) {
                    return false;
                  }
                })
            .get();

    // then the earlier binding did not leak onto the worker thread
    assertThat(resolvedPhysicalTenant).isFalse();
  }
}
