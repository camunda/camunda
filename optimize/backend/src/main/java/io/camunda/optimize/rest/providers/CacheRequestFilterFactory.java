/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Provider
public class CacheRequestFilterFactory implements DynamicFeature {

  @Override
  public void configure(final ResourceInfo resourceInfo, final FeatureContext context) {
    final CacheRequest cacheRequest =
        resourceInfo.getResourceMethod().getAnnotation(CacheRequest.class);

    if (cacheRequest != null) {
      context.register(new CacheRequestFilter());
    }
  }

  static class CacheRequestFilter implements ContainerResponseFilter {

    @Override
    public void filter(
        final ContainerRequestContext containerRequestContext,
        final ContainerResponseContext containerResponseContext) {
      if (!containerResponseContext.getHeaders().containsKey(HttpHeaders.CACHE_CONTROL)
          && Response.Status.Family.familyOf(containerResponseContext.getStatus())
              .equals(Response.Status.Family.SUCCESSFUL)) {
        containerResponseContext
            .getHeaders()
            .putSingle(HttpHeaders.CACHE_CONTROL, "max-age=" + getSecondsToMidnight());
      }
    }

    private String getSecondsToMidnight() {
      final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
      final OffsetDateTime tomorrowDayStart = now.plusDays(1).truncatedTo(ChronoUnit.DAYS);
      final Duration timeToMidnight = Duration.between(now, tomorrowDayStart);

      return String.valueOf(timeToMidnight.getSeconds());
    }
  }
}
