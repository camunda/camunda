/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import static io.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.tomcat.CacheRequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

@ExtendWith(MockitoExtension.class)
public class CacheRequestFilterTest {

  final ModelAndView modelAndView = null;
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HandlerMethod handlerMethod = mock(HandlerMethod.class);
  private final Method method = mock(Method.class);
  private final CacheRequestInterceptor underTest = new CacheRequestInterceptor();

  {
    when(handlerMethod.getMethod()).thenReturn(method);
    when(method.isAnnotationPresent(any())).thenReturn(true);
  }

  @Test
  public void filterSetsCacheControlMaxAge() throws Exception {
    // given
    when(response.getStatus()).thenReturn(HttpStatus.OK.value());
    final OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.postHandle(request, response, handlerMethod, modelAndView);

    // then
    verify(response).addHeader(HttpHeaders.CACHE_CONTROL, "max-age=21600");
  }

  @Test
  public void filterDoesNotOverwritePreviousCacheControlHeaders() throws Exception {
    // given
    when(response.getHeader(HttpHeaders.CACHE_CONTROL)).thenReturn(CACHE_CONTROL_NO_STORE);

    // when
    underTest.postHandle(request, response, handlerMethod, modelAndView);

    // then
    verify(response, times(0)).addHeader(any(), any());
  }

  @ParameterizedTest
  @MethodSource("unsuccessfulResponses")
  public void filterIsNotSetOnUnsuccessfulResponse(final HttpStatus status) throws Exception {
    // given
    when(response.getStatus()).thenReturn(status.value());
    final OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.postHandle(request, response, handlerMethod, modelAndView);

    // then
    assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isNull();
  }

  private static Stream<HttpStatus> unsuccessfulResponses() {
    return Stream.of(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
  }
}
