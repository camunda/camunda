/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Aligns Spring MVC's async-request timeout with the long-poll timeout so that long-poll endpoints
 * (notably {@code /v2/jobs/activation}) don't have their {@link
 * java.util.concurrent.CompletableFuture} forcibly errored with {@code
 * AsyncRequestTimeoutException} before the broker has a chance to return.
 *
 * <p>Background: {@code JobController.activateJobs(...)} returns a {@code
 * CompletableFuture<ResponseEntity<Object>>}. Spring MVC adapts that to a {@code DeferredResult}
 * via {@code request.startAsync()}. The async-timeout default falls through to the servlet
 * container — Tomcat's default is 30 s. The long-poll request itself can run for 10 s ({@code
 * DEFAULT_LONG_POLLING_TIMEOUT}) up to whatever the client passes as {@code requestTimeout} in the
 * activation request body, which is typically tens of seconds and may exceed 30 s. When that
 * happens, the async timeout fires first, the {@code DeferredResult} is completed with {@code
 * AsyncRequestTimeoutException}, the response goes out as a 503, and the client treats it as a
 * failed request — re-issuing it through the full Spring Security chain. The wasted cycle plus
 * retry amplifies load and depresses effective throughput on the protected REST API.
 *
 * <p>Configuration property:
 *
 * <ul>
 *   <li>{@code camunda.gateway.rest.async.request-timeout-ms} (default {@code 120_000} = 2 min):
 *       upper bound on how long Spring MVC will let an async request remain pending before forcing
 *       it to error.
 * </ul>
 *
 * <p>The default of 2 min is generous: long-poll defaults to 10 s; even if a client supplies a
 * 60-second request timeout, the async timeout fires only when the broker is genuinely stuck —
 * which is the situation you actually want to surface, not racing the connection deadline.
 *
 * <p>Refs camunda/camunda#35067.
 */
@Configuration
public class AsyncSupportConfig implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(AsyncSupportConfig.class);

  private final long asyncRequestTimeoutMs;

  public AsyncSupportConfig(
      @Value("${camunda.gateway.rest.async.request-timeout-ms:120000}")
          final long asyncRequestTimeoutMs) {
    this.asyncRequestTimeoutMs = asyncRequestTimeoutMs;
  }

  @Override
  public void configureAsyncSupport(final AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(asyncRequestTimeoutMs);
    LOG.info(
        "Spring MVC async request timeout set to {} ms (overriding servlet-container default)",
        asyncRequestTimeoutMs);
  }
}
