/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpenTelemetryFilter extends OncePerRequestFilter {

  private static final String SPAN_REQUEST_ATTR = "OpenTelemetryFilter.SPAN";
  private final Tracer tracer = GlobalOpenTelemetry.getTracer("io.camunda.gateway");

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final String spanName = request.getMethod() + " " + request.getRequestURI();
    final Span span =
        tracer.spanBuilder("remco his test span").setSpanKind(SpanKind.SERVER).startSpan();

    // put span on request so async listener can find it
    request.setAttribute(SPAN_REQUEST_ATTR, span);

    try (final Scope ignored = span.makeCurrent()) {
      // add basic attributes
      span.setAttribute("http.method", request.getMethod());
      span.setAttribute("http.target", request.getRequestURI());

      filterChain.doFilter(request, response);

      // if async started, end in AsyncListener; else end now
      if (request.isAsyncStarted()) {
        try {
          request
              .getAsyncContext()
              .addListener(
                  new AsyncListener() {
                    @Override
                    public void onComplete(final AsyncEvent event) {
                      endSpan(span, (HttpServletResponse) event.getSuppliedResponse());
                    }

                    @Override
                    public void onTimeout(final AsyncEvent event) {
                      span.setAttribute("async.timeout", true);
                      endSpan(span, (HttpServletResponse) event.getSuppliedResponse());
                    }

                    @Override
                    public void onError(final AsyncEvent event) {
                      final Throwable t = event.getThrowable();
                      if (t != null) {
                        span.recordException(t);
                      }
                      endSpan(span, (HttpServletResponse) event.getSuppliedResponse());
                    }

                    @Override
                    public void onStartAsync(final AsyncEvent event) {
                      // no-op
                    }
                  });
        } catch (final IllegalStateException ex) {
          // If async context disappeared, end here
          endSpan(span, response);
        }
      } else {
        endSpan(span, response);
      }
    } catch (final Throwable t) {
      span.recordException(t);
      span.setAttribute("http.status_code", response.getStatus());
      span.end();
      throw t;
    } finally {
      // cleanup attribute if present
      request.removeAttribute(SPAN_REQUEST_ATTR);
    }
  }

  private void endSpan(final Span span, final HttpServletResponse response) {
    try {
      span.setAttribute("http.status_code", response.getStatus());
    } finally {
      span.end();
    }
  }
}
