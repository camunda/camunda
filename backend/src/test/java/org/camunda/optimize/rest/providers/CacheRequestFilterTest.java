/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CacheRequestFilterTest {

  private CacheRequestFilterFactory.CacheRequestFilter underTest;

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  ContainerResponseContext responseContext;

  @BeforeEach
  public void setup() {
    underTest = new CacheRequestFilterFactory.CacheRequestFilter();
    when(responseContext.getHeaders()).thenReturn(new MultivaluedHashMap<>());
  }

  @Test
  public void filter_setsCacheControlMaxAge() {
    // given
    when(responseContext.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("max-age=21600");
  }

  @Test
  public void filter_doesNotOverwritePreviousCacheControlHeaders() {
    // given
    responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().get(HttpHeaders.CACHE_CONTROL)).hasSize(1);
    assertThat(responseContext.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo(CACHE_CONTROL_NO_STORE);
  }

  @ParameterizedTest
  @MethodSource("unsuccessfulResponses")
  public void filter_isNotSetOnUnsuccessfulResponse(Response.Status errorResponse) {
    // given
    when(responseContext.getStatus()).thenReturn(errorResponse.getStatusCode());
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().get(HttpHeaders.CACHE_CONTROL)).isNull();
  }

  private static Stream<Response.Status> unsuccessfulResponses() {
    return Stream.of(
      Response.Status.BAD_REQUEST,
      Response.Status.NOT_FOUND,
      Response.Status.FORBIDDEN
    );
  }

}
