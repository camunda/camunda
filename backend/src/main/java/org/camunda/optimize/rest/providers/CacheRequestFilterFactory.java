/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

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
import org.camunda.optimize.service.security.util.LocalDateUtil;

@Provider
public class CacheRequestFilterFactory implements DynamicFeature {

  @Override
  public void configure(final ResourceInfo resourceInfo, final FeatureContext context) {
    CacheRequest cacheRequest = resourceInfo.getResourceMethod().getAnnotation(CacheRequest.class);

    if (cacheRequest != null) {
      context.register(new CacheRequestFilter());
    }
  }

  static class CacheRequestFilter implements ContainerResponseFilter {

    @Override
    public void filter(
        ContainerRequestContext containerRequestContext,
        ContainerResponseContext containerResponseContext) {
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
      Duration timeToMidnight = Duration.between(now, tomorrowDayStart);

      return String.valueOf(timeToMidnight.getSeconds());
    }
  }
}
