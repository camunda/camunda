/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
    String redirectUrl = "http://redirecturl";
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
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");
    IdentityAuthentication identityAuthentication = new IdentityAuthentication();
    String fakeContextPath = "contextPath";

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
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");
    IdentityAuthentication identityAuthentication = new IdentityAuthentication();
    String fakeContextPath = "contextPath";
    String fakeOriginalUrl = "/fakeOriginalUrl";

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
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");

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
    AuthCodeDto authCodeDto = new AuthCodeDto("code", "state", "error");

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
