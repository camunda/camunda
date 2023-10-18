/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.operate.webapp.security.OperateURIs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdentityControllerTest {

  private IdentityController underTest;

  @Mock
  private IdentityService mockIdentityService;

  @Mock
  private HttpServletRequest mockServletRequest;

  @Mock
  private HttpServletResponse mockServletResponse;

  @Mock
  private HttpSession mockSession;

  @Mock
  private SecurityContext mockSecurityContext;

  @Mock
  private SecurityContextHolderStrategy mockSecurityContextHolderStrategy;

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
    String redirectUrl = "http://redirecturl";
    when(mockIdentityService.getRedirectUrl(mockServletRequest)).thenReturn(redirectUrl);

    assertThat(underTest.login(mockServletRequest)).isEqualTo("redirect:" + redirectUrl);
  }

  @Test
  public void testNoPermissions() {
    assertThat(underTest.noPermissions()).isEqualTo(
            "No permission for Operate - Please check your operate configuration or cloud configuration.");
  }

  @Test
  public void testLoggedInCallbackWithRedirectToRoot() throws Exception {
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");
    IdentityAuthentication identityAuthentication = new IdentityAuthentication();
    String fakeContextPath = "contextPath";

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(mockServletRequest, authCodeDto)).thenReturn(identityAuthentication);
    when(mockSecurityContextHolderStrategy.createEmptyContext()).thenReturn(mockSecurityContext);
    when(mockServletRequest.getContextPath()).thenReturn(fakeContextPath);
    when(mockSession.getAttribute(any())).thenReturn(null);

    underTest.loggedInCallback(mockServletRequest, mockServletResponse, authCodeDto);

    verify(mockSecurityContext, times(1)).setAuthentication(identityAuthentication);
    verify(mockSecurityContextHolderStrategy, times(1)).setContext(mockSecurityContext);
    verify(mockServletResponse, times(1)).sendRedirect(fakeContextPath + OperateURIs.ROOT);
  }

  @Test
  public void testLoggedInCallbackWithRedirectToOriginal() throws Exception {
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");
    IdentityAuthentication identityAuthentication = new IdentityAuthentication();
    String fakeContextPath = "contextPath";
    String fakeOriginalUrl = "/fakeOriginalUrl";

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(mockServletRequest, authCodeDto)).thenReturn(identityAuthentication);
    when(mockSecurityContextHolderStrategy.createEmptyContext()).thenReturn(mockSecurityContext);
    when(mockServletRequest.getContextPath()).thenReturn(fakeContextPath);
    when(mockSession.getAttribute(any())).thenReturn(fakeOriginalUrl);

    underTest.loggedInCallback(mockServletRequest, mockServletResponse, authCodeDto);

    verify(mockSecurityContext, times(1)).setAuthentication(identityAuthentication);
    verify(mockSecurityContextHolderStrategy, times(1)).setContext(mockSecurityContext);
    verify(mockServletResponse, times(1)).sendRedirect(fakeContextPath + fakeOriginalUrl);
  }

  @Test
  public void testLoggedInCallbackWithExceptionAndNullSecurityContext() throws Exception {
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(mockServletRequest, authCodeDto)).thenThrow(new Exception());
    when(mockSecurityContextHolderStrategy.getContext()).thenReturn(null);

    underTest.loggedInCallback(mockServletRequest, mockServletResponse, authCodeDto);

    verifyNoInteractions(mockSecurityContext);
    verify(mockSession, times(1)).invalidate();
    verify(mockSecurityContextHolderStrategy, times(0)).clearContext();
    verify(mockServletResponse, times(1)).sendRedirect(OperateURIs.NO_PERMISSION);
  }

  @Test
  public void testLoggedInCallbackWithExceptionAndExistingSecurityContext() throws Exception {
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");

    when(mockServletRequest.getSession()).thenReturn(mockSession);
    when(mockIdentityService.getAuthenticationFor(mockServletRequest, authCodeDto)).thenThrow(new Exception());
    when(mockSecurityContextHolderStrategy.getContext()).thenReturn(mockSecurityContext);

    underTest.loggedInCallback(mockServletRequest, mockServletResponse, authCodeDto);

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
