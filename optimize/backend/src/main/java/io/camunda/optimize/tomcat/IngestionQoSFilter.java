/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;

/**
 * This quality of service filter applies to external data ingestion, limiting the max number of
 * requests that can be served at a time. It is based heavily on Jetty's QoSFilter, with changes
 * mainly in the response we give on rejected requests.
 * https://www.eclipse.org/jetty/javadoc/9.4.26.v20200117/org/eclipse/jetty/servlets/QoSFilter.html
 */
public class IngestionQoSFilter implements Filter {

  public static final String RETRY_AFTER_SECONDS = "5";
  private static final String TOO_MANY_REQUESTS = "Too many requests";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(IngestionQoSFilter.class);
  private final long waitMs = 50;
  private final long suspendMs = 500;
  private int maxRequests = 10;
  private Semaphore passes;
  private Queue<AsyncContext>[] queues;
  private AsyncListener[] listeners;
  private final Callable<Integer> maxRequestCountProvider;
  private final String suspended =
      "IngestionQoSFilter@" + Integer.toHexString(hashCode()) + ".SUSPENDED";
  private final String resumed =
      "IngestionQoSFilter@" + Integer.toHexString(hashCode()) + ".RESUMED";

  public IngestionQoSFilter(final Callable<Integer> maxRequestCountProvider) {
    this.maxRequestCountProvider = maxRequestCountProvider;
  }

  @Override
  public void init(final FilterConfig filterConfig) {
    // We can use simpler initialization than the Jetty QoS filter as we use default configuration
    final int maxPriority = 2;
    queues = new Queue[maxPriority + 1];
    listeners = new AsyncListener[queues.length];
    for (int p = 0; p < queues.length; ++p) {
      queues[p] = new ConcurrentLinkedQueue<>();
      listeners[p] = new QoSAsyncListener(p);
    }
    passes = new Semaphore(maxRequests, true);
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    // This is the only configurable property. It cannot be set during initialization so needs
    // setting here instead
    if (maxRequests != getMaxRequestsFromProvider()) {
      setMaxRequests(getMaxRequestsFromProvider());
    }

    boolean accepted = false;
    try {
      final Boolean suspended = (Boolean) request.getAttribute(this.suspended);
      if (suspended == null) {
        accepted = passes.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
        if (accepted) {
          request.setAttribute(this.suspended, Boolean.FALSE);
          LOG.debug("Accepted {}", request);
        } else {
          request.setAttribute(this.suspended, Boolean.TRUE);
          final int priority = getPriority(request);
          AsyncContext asyncContext = request.startAsync();
          final long suspendMs = this.suspendMs;
          if (suspendMs > 0) {
            asyncContext.setTimeout(suspendMs);
          }
          asyncContext.addListener(listeners[priority]);

          // Spring Security wraps the asyncContext into
          // HttpServlet3RequestFactory$SecurityContextAsyncContext
          // we need to unwrap it for the filter to work properly
          try {
            asyncContext = (AsyncContext) FieldUtils.readField(asyncContext, "asyncContext", true);
          } catch (final IllegalAccessException e) {
            throw new OptimizeRuntimeException(e);
          }

          queues[priority].add(asyncContext);
          LOG.debug("Suspended {}", request);
          return;
        }
      } else {
        if (suspended) {
          request.setAttribute(this.suspended, Boolean.FALSE);
          final Boolean resumed = (Boolean) request.getAttribute(this.resumed);
          if (Boolean.TRUE.equals(resumed)) {
            passes.acquire();
            accepted = true;
            LOG.debug("Resumed {}", request);
          } else {
            // Timeout! try 1 more time.
            accepted = passes.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
            LOG.debug("Timeout {}", request);
          }
        } else {
          // Pass through resume of previously accepted request.
          passes.acquire();
          accepted = true;
          LOG.debug("Passthrough {}", request);
        }
      }

      if (accepted) {
        chain.doFilter(request, response);
      } else {
        LOG.debug("Rejected {}", request);
        sendErrorResponse(response);
      }
    } catch (final InterruptedException e) {
      sendErrorResponse(response);
      Thread.currentThread().interrupt();
    } finally {
      if (accepted) {
        passes.release();
        for (int p = queues.length - 1; p >= 0; --p) {
          final AsyncContext asyncContext = queues[p].poll();
          if (asyncContext != null) {
            final ServletRequest candidate = asyncContext.getRequest();
            final Boolean suspended = (Boolean) candidate.getAttribute(this.suspended);
            if (Boolean.TRUE.equals(suspended)) {
              try {
                candidate.setAttribute(resumed, Boolean.TRUE);
                asyncContext.dispatch();
                break;
              } catch (final IllegalStateException x) {
                LOG.warn(x.getMessage());
                continue;
              }
            }
          }
        }
      }
    }
  }

  @Override
  public void destroy() {}

  private int getPriority(final ServletRequest request) {
    // We use the default prioritization for requests, as per the Jetty QoSFilter
    final HttpServletRequest baseRequest = (HttpServletRequest) request;
    if (baseRequest.getUserPrincipal() != null) {
      return 2;
    } else {
      final HttpSession session = baseRequest.getSession(false);
      if (session != null && !session.isNew()) {
        return 1;
      } else {
        return 0;
      }
    }
  }

  private void setMaxRequests(final int value) {
    LOG.info("setting the max number of ingestion requests to {}", value);
    passes = new Semaphore((value - maxRequests + passes.availablePermits()), true);
    maxRequests = value;
  }

  protected void sendErrorResponse(final ServletResponse servletResponse) throws IOException {
    // We send a different error response than Jetty QoSFilter plus a required header in line with
    // CloudEvent specification
    ((HttpServletResponse) servletResponse).setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
    ((HttpServletResponse) servletResponse)
        .sendError(HttpStatus.TOO_MANY_REQUESTS.value(), TOO_MANY_REQUESTS);
  }

  private int getMaxRequestsFromProvider() {
    try {
      return maxRequestCountProvider.call();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IngestionQoSFilter;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "IngestionQoSFilter(suspended="
        + suspended
        + ", resumed="
        + resumed
        + ", waitMs="
        + waitMs
        + ", suspendMs="
        + suspendMs
        + ", maxRequests="
        + maxRequests
        + ", passes="
        + passes
        + ", queues="
        + java.util.Arrays.deepToString(queues)
        + ", listeners="
        + java.util.Arrays.deepToString(listeners)
        + ", maxRequestCountProvider="
        + maxRequestCountProvider
        + ")";
  }

  private class QoSAsyncListener implements AsyncListener {

    private final int priority;

    public QoSAsyncListener(final int priority) {
      this.priority = priority;
    }

    @Override
    public void onComplete(final AsyncEvent event) throws IOException {}

    @Override
    public void onTimeout(final AsyncEvent event) throws IOException {
      // Remove before it's redispatched, so it won't be
      // redispatched again at the end of the filtering.
      final AsyncContext asyncContext = event.getAsyncContext();
      queues[priority].remove(asyncContext);
      sendErrorResponse(event.getSuppliedResponse());
      asyncContext.complete();
    }

    @Override
    public void onError(final AsyncEvent event) throws IOException {}

    @Override
    public void onStartAsync(final AsyncEvent event) throws IOException {}
  }
}
