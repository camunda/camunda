/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sets the {@code instance} of a problem detail response to the request path when it is missing.
 *
 * <p>Spring Framework 7.1 stopped doing this, but our REST API specification requires the field.
 */
@NullMarked
@Configuration
public class ProblemDetailInstanceConfig implements WebMvcConfigurer {

  @Override
  public void addErrorResponseInterceptors(final List<ErrorResponse.Interceptor> interceptors) {
    interceptors.add(new RequestPathInstanceInterceptor());
  }

  static final class RequestPathInstanceInterceptor implements ErrorResponse.Interceptor {

    @Override
    public void handleError(
        final ProblemDetail detail,
        final @Nullable ErrorResponse errorResponse,
        final HttpRequest request) {
      if (detail.getInstance() == null) {
        detail.setInstance(URI.create(request.getURI().getRawPath()));
      }
    }

    @Override
    public void handleError(final ProblemDetail detail, final @Nullable ErrorResponse response) {
      // Spring always calls the request-aware variant above.
    }
  }
}
