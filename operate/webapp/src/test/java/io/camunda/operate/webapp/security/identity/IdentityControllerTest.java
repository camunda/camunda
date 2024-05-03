/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.operate.webapp.security.OperateURIs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

@ExtendWith(MockitoExtension.class)
public class IdentityControllerTest {

  private IdentityController underTest;

  @Mock private IdentityService mockIdentityService;

  @Mock private HttpServletRequest mockServletRequest;

  @Mock private HttpServletResponse mockServletResponse;

  @Mock private HttpSession mockSession;

  @Mock private SecurityContext mockSecurityContext;

  @Mock private SecurityContextHolderStrategy mockSecurityContextHolderStrategy;

  @BeforeEach
  public void setup() {
    SecurityContextHolder.setContextHolderStrategy(mockSecurityContextHolderStrategy);
    underTest = new IdentityController(mockIdentityService);
  }

  @AfterEach
  public void cleanup() {
    SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
  }

  @Test
  public void testLogin() {
    final String redirectUrl = "http://redirecturl";
    when(mockIdentityService.getRedirectUrl(mockServletRequest)).thenReturn(redirectUrl);

    assertThat(underTest.login(mockServletRequest)).isEqualTo("redirect:" + redirectUrl);
  }

  @Test
  public void testNoPermissions() {
    assertThat(underTest.noPermissions())
        .isEqualTo(
            "No permission for Operate - Please check your operate configuration or cloud configuration.");
  }

  @Test
  public void testLoggedInCallbackWithRedirectToRoot() throws Exception {
    final AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");
    final IdentityAuthentication identityAuthentication = new IdentityAuthentication();
    final String fakeContextPath = "contextPath";

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(eq(mockServletRequest), any(AuthCodeDto.class)))
        .thenReturn(identityAuthentication);
    when(mockSecurityContextHolderStrategy.createEmptyContext()).thenReturn(mockSecurityContext);
    when(mockServletRequest.getContextPath()).thenReturn(fakeContextPath);
    when(mockSession.getAttribute(any())).thenReturn(null);

    underTest.loggedInCallback(
        mockServletRequest,
        mockServletResponse,
        authCodeDto.getCode(),
        authCodeDto.getState(),
        authCodeDto.getError());

    verify(mockSecurityContext, times(1)).setAuthentication(identityAuthentication);
    verify(mockSecurityContextHolderStrategy, times(1)).setContext(mockSecurityContext);
    verify(mockServletResponse, times(1)).sendRedirect(fakeContextPath + OperateURIs.ROOT);
  }

  @Test
  public void testLoggedInCallbackWithRedirectToOriginal() throws Exception {
    final AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");
    final IdentityAuthentication identityAuthentication = new IdentityAuthentication();
    final String fakeContextPath = "contextPath";
    final String fakeOriginalUrl = "/fakeOriginalUrl";

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(eq(mockServletRequest), any(AuthCodeDto.class)))
        .thenReturn(identityAuthentication);
    when(mockSecurityContextHolderStrategy.createEmptyContext()).thenReturn(mockSecurityContext);
    when(mockServletRequest.getContextPath()).thenReturn(fakeContextPath);
    when(mockSession.getAttribute(any())).thenReturn(fakeOriginalUrl);

    underTest.loggedInCallback(
        mockServletRequest,
        mockServletResponse,
        authCodeDto.getCode(),
        authCodeDto.getState(),
        authCodeDto.getError());

    verify(mockSecurityContext, times(1)).setAuthentication(identityAuthentication);
    verify(mockSecurityContextHolderStrategy, times(1)).setContext(mockSecurityContext);
    verify(mockServletResponse, times(1)).sendRedirect(fakeContextPath + fakeOriginalUrl);
  }

  @Test
  public void testLoggedInCallbackWithExceptionAndNullSecurityContext() throws Exception {
    final AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(eq(mockServletRequest), any(AuthCodeDto.class)))
        .thenThrow(new Exception());
    when(mockSecurityContextHolderStrategy.getContext()).thenReturn(null);

    underTest.loggedInCallback(
        mockServletRequest,
        mockServletResponse,
        authCodeDto.getCode(),
        authCodeDto.getState(),
        authCodeDto.getError());

    verifyNoInteractions(mockSecurityContext);
    verify(mockSession, times(1)).invalidate();
    verify(mockSecurityContextHolderStrategy, times(0)).clearContext();
    verify(mockServletResponse, times(1)).sendRedirect(OperateURIs.NO_PERMISSION);
  }

  @Test
  public void testLoggedInCallbackWithExceptionAndExistingSecurityContext() throws Exception {
    final AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(eq(mockServletRequest), any(AuthCodeDto.class)))
        .thenThrow(new Exception());
    when(mockSecurityContextHolderStrategy.getContext()).thenReturn(mockSecurityContext);

    underTest.loggedInCallback(
        mockServletRequest,
        mockServletResponse,
        authCodeDto.getCode(),
        authCodeDto.getState(),
        authCodeDto.getError());

    verify(mockSession, times(1)).invalidate();
    verify(mockSecurityContext, times(1)).setAuthentication(null);
    verify(mockSecurityContextHolderStrategy, times(1)).clearContext();
    verify(mockServletResponse, times(1)).sendRedirect(OperateURIs.NO_PERMISSION);
  }

  @Test
  public void testLogoutWithNullSecurityContext() throws IOException {
    when(mockServletRequest.getSession()).thenReturn(mockSession);

    underTest.logout(mockServletRequest, mockServletResponse);

    verifyNoInteractions(mockSecurityContext);
    verify(mockSession, times(1)).invalidate();
    verify(mockSecurityContextHolderStrategy, times(0)).clearContext();
  }

  @Test
  public void testLogoutWithExistingSecurityContext() throws IOException {
    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockSecurityContextHolderStrategy.getContext()).thenReturn(mockSecurityContext);

    underTest.logout(mockServletRequest, mockServletResponse);

    verify(mockSession, times(1)).invalidate();
    verify(mockSecurityContext, times(1)).setAuthentication(null);
    verify(mockSecurityContextHolderStrategy, times(1)).clearContext();
  }
}
