/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;

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
    Optional<String> token = AuthCookieService.getAuthCookieToken(requestMock);

    // then
    assertThat(token).isPresent().get().isEqualTo("test");
  }

  @Test
  public void getTokenExceptionFromContainerRequestContext() {
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    assertThat(AuthCookieService.getAuthCookieToken(requestMock)).isEmpty();
  }

  @Test
  public void getTokenFromHttpServletRequest() {
    // given
    String authorizationHeader = String.format("Bearer %s", "test");
    javax.servlet.http.Cookie[] cookies = {new javax.servlet.http.Cookie(OPTIMIZE_AUTHORIZATION, authorizationHeader)};
    HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    Mockito.when(servletRequestMock.getCookies()).thenReturn(cookies);

    // when
    Optional<String> token = AuthCookieService.getAuthCookieToken(servletRequestMock);

    // then
    assertThat(token).isPresent().get().isEqualTo("test");
  }

  @Test
  public void getTokenExceptionFromHttpServletRequest() {
    HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    assertThat(AuthCookieService.getAuthCookieToken(servletRequestMock)).isEmpty();
  }
}
