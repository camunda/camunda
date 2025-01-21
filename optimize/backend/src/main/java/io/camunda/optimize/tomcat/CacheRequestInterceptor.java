/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class CacheRequestInterceptor implements HandlerInterceptor {
  private final Logger log = LoggerFactory.getLogger(CacheRequestInterceptor.class);

  @Override
  public void postHandle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Object handler,
      @Nullable final ModelAndView modelAndView)
      throws Exception {
    final boolean isSuccessfull =
        Optional.ofNullable(HttpStatus.resolve(response.getStatus()))
            .map(HttpStatus::is2xxSuccessful)
            .orElse(false);

    if (!response.containsHeader(HttpHeaders.CACHE_CONTROL)
        && isSuccessfull
        && hasCacheRequestAnnotation(handler)) {
      log.info(
          "Adding header "
              + HttpHeaders.CACHE_CONTROL
              + ": max-age="
              + getSecondsToMidnight()
              + " to response for request "
              + request.getRequestURI());
      response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + getSecondsToMidnight());
    }
  }

  private boolean hasCacheRequestAnnotation(final Object handler) {
    if (handler instanceof final HandlerMethod handlerMethod) {
      final Method method = handlerMethod.getMethod();
      return method.isAnnotationPresent(CacheRequest.class);
    }
    return false;
  }

  private String getSecondsToMidnight() {
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final OffsetDateTime tomorrowDayStart = now.plusDays(1).truncatedTo(ChronoUnit.DAYS);
    final Duration timeToMidnight = Duration.between(now, tomorrowDayStart);

    return String.valueOf(timeToMidnight.getSeconds());
  }
}
