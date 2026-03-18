/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Interceptor that detects client disconnects during long-polling job activation requests by
 * periodically probing the connection with a write+flush. If the write fails (client gone), the
 * associated {@link CompletableFuture} is cancelled, which causes the long-polling handler to
 * detect the cancellation via {@code isCancelled()} and remove the stale request from the pending
 * queue before it can activate jobs for a dead connection.
 *
 * <p>This is the server-side equivalent of gRPC's {@code RST_STREAM} detection. Without it, the
 * server has no way to know the client is gone — TCP disconnect is not detectable via Tomcat's
 * {@code AsyncListener.onError()} and Apache HttpClient's {@code CloseMode.IMMEDIATE} does not
 * reliably close sockets due to NIO reactor thread races.
 *
 * <p>The probe writes single space characters which are insignificant whitespace in JSON (RFC
 * 8259), so the client receives {@code " {"jobs":[...]}"} which parses identically.
 */
public class LongPollingDisconnectInterceptor implements AsyncHandlerInterceptor, DisposableBean {

  /**
   * Request attribute key used to pass the {@link CompletableFuture} from the controller to this
   * interceptor.
   */
  public static final String ACTIVATE_JOBS_FUTURE_ATTR =
      "io.camunda.zeebe.gateway.rest.activateJobsFuture";

  private static final Logger LOG = LoggerFactory.getLogger(LongPollingDisconnectInterceptor.class);

  private static final long PROBE_INTERVAL_MS = 500;

  private final ScheduledExecutorService scheduler;

  {
    final var executor =
        new ScheduledThreadPoolExecutor(
            1,
            r -> {
              final var thread = new Thread(r, "long-poll-probe");
              thread.setDaemon(true);
              return thread;
            });
    executor.setRemoveOnCancelPolicy(true);
    scheduler = executor;
  }

  @Override
  public void afterConcurrentHandlingStarted(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
    final var future = (CompletableFuture<?>) request.getAttribute(ACTIVATE_JOBS_FUTURE_ATTR);

    if (future == null || !request.isAsyncStarted()) {
      return;
    }

    // Set Content-Type before any probe write. The first write+flush commits Tomcat's response
    // headers — without this, the client receives a response without application/json and
    // cannot parse it.
    response.setContentType("application/json");

    final var active = new AtomicBoolean(true);
    // Lock ensures a probe write cannot overlap with the future completion, preventing
    // whitespace from landing in the middle of Spring's JSON response serialization.
    final var lock = new ReentrantLock();

    final ScheduledFuture<?> probeTask =
        scheduler.scheduleAtFixedRate(
            () -> probeConnection(response, future, active, lock),
            PROBE_INTERVAL_MS,
            PROBE_INTERVAL_MS,
            TimeUnit.MILLISECONDS);

    // Stop probing when the future completes (normally, exceptionally, or cancelled).
    // Acquiring the lock guarantees no probe write is in-flight when we deactivate.
    future.whenComplete(
        (result, ex) -> {
          lock.lock();
          try {
            active.set(false);
          } finally {
            lock.unlock();
          }
          probeTask.cancel(false);
        });
  }

  @Override
  public void destroy() {
    scheduler.shutdownNow();
  }

  private void probeConnection(
      final HttpServletResponse response,
      final CompletableFuture<?> future,
      final AtomicBoolean active,
      final ReentrantLock lock) {
    if (!lock.tryLock()) {
      return; // completion in progress, skip this probe
    }
    try {
      if (!active.get()) {
        return;
      }
      final var out = response.getOutputStream();
      out.write(' ');
      out.flush();
    } catch (final Exception e) {
      LOG.debug(
          "Long-poll connection probe failed ({}), cancelling activation request", e.getMessage());
      active.set(false);
      future.cancel(true);
    } finally {
      lock.unlock();
    }
  }
}
