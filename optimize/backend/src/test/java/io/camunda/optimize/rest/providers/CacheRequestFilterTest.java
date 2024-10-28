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
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CacheRequestFilterTest {

  @Mock ContainerRequestContext requestContext;
  @Mock ContainerResponseContext responseContext;
  private CacheRequestFilterFactory.CacheRequestFilter underTest;

  @BeforeEach
  public void setup() {
    underTest = new CacheRequestFilterFactory.CacheRequestFilter();
    when(responseContext.getHeaders()).thenReturn(new MultivaluedHashMap<>());
  }

  @Test
  public void filterSetsCacheControlMaxAge() {
    // given
    when(responseContext.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    final OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
        .isEqualTo("max-age=21600");
  }

  @Test
  public void filterDoesNotOverwritePreviousCacheControlHeaders() {
    // given
    responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
    assertThat(responseContext.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
        .isEqualTo(CACHE_CONTROL_NO_STORE);
  }

  @ParameterizedTest
  @MethodSource("unsuccessfulResponses")
  public void filterIsNotSetOnUnsuccessfulResponse(final Response.Status errorResponse) {
    // given
    when(responseContext.getStatus()).thenReturn(errorResponse.getStatusCode());
    final OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().get(HttpHeaders.CACHE_CONTROL)).isNull();
  }

  private static Stream<Response.Status> unsuccessfulResponses() {
    return Stream.of(
        Response.Status.BAD_REQUEST, Response.Status.NOT_FOUND, Response.Status.FORBIDDEN);
  }
}
