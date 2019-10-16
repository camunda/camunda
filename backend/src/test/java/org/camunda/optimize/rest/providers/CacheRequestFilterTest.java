/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CacheRequestFilterTest {

  CacheRequestFilterFactory.CacheRequestFilter underTest;

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  ContainerResponseContext responseContext;

  @BeforeEach
  public void setup() {
    underTest = new CacheRequestFilterFactory.CacheRequestFilter();
    MultivaluedMap<String, Object> headersMap = new MultivaluedHashMap<>();
    when(responseContext.getHeaders()).thenReturn(headersMap);
  }

  @Test
  public void filter_setsCacheControlMaxAge() {
    // given
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL), is("max-age=21600"));
  }

  @Test
  public void filter_overwritesPreviousCacheControlHeaders() {
    // given
    responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "no-store");
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when
    underTest.filter(requestContext, responseContext);

    // then
    assertThat(responseContext.getHeaders().get(HttpHeaders.CACHE_CONTROL).size(), is(1));
    assertThat(responseContext.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL), is("max-age=21600"));
  }

}