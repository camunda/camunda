/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AuthCookieServiceTest {

  @Test
  public void getTokenFromContainerRequestContext() {
    // given
    String authorizationHeader = String.format("Bearer %s", "test");
    Cookie cookie = new Cookie(OPTIMIZE_AUTHORIZATION, authorizationHeader);
    Map<String, Cookie> cookies = Collections.singletonMap(OPTIMIZE_AUTHORIZATION, cookie);
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    Mockito.when(requestMock.getCookies()).thenReturn(cookies);

    // when
    Optional<String> token = AuthCookieService.getToken(requestMock);

    // then
    assertThat(token.isPresent(), is(true));
    assertThat(token.get(), is("test"));
  }

  @Test
  public void getTokenExceptionFromContainerRequestContext() {
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    assertThat(AuthCookieService.getToken(requestMock), is(Optional.empty()));
  }

  @Test
  public void getTokenFromHttpServletRequest() {
    // given
    String authorizationHeader = String.format("Bearer %s", "test");
    javax.servlet.http.Cookie[] cookies = {new javax.servlet.http.Cookie(OPTIMIZE_AUTHORIZATION, authorizationHeader)};
    HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    Mockito.when(servletRequestMock.getCookies()).thenReturn(cookies);

    // when
    Optional<String> token = AuthCookieService.getToken(servletRequestMock);

    // then
    assertThat(token.isPresent(), is(true));
    assertThat(token.get(), is("test"));
  }

  @Test
  public void getTokenExceptionFromHttpServletRequest() {
    HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    assertThat(AuthCookieService.getToken(servletRequestMock), is(Optional.empty()));
  }
}