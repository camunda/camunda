/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.active.ActiveUserChecker.StoredUser;
import io.camunda.authentication.source.LoginSources;
import io.camunda.authentication.source.LoginSources.Source;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class InactiveUserFilterTest {

  private final CamundaAuthenticationProvider provider = mock(CamundaAuthenticationProvider.class);
  private final ActiveUserChecker checker = mock(ActiveUserChecker.class);
  private final FilterChain chain = mock(FilterChain.class);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/topology");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final InactiveUserFilter filter = new InactiveUserFilter(provider, checker);

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("jane", "n/a", "ROLE_USER"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static CamundaAuthentication user(final String username) {
    return CamundaAuthentication.of(b -> b.user(username));
  }

  @Test
  void shouldRejectWithForbiddenWhenUserIsInactive() throws Exception {
    // given
    when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
    when(checker.isActive("default", "jane")).thenReturn(false);

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("\"status\":403", "/v2/topology");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void shouldPassWhenUserIsActive() throws Exception {
    // given
    when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
    when(checker.isActive("default", "jane")).thenReturn(true);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldPassWithoutLookupWhenAuthenticationHasNoUsername() throws Exception {
    // given
    when(provider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.clientId("client")));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(checker, never()).isActive(any(), any());
  }

  @Test
  void shouldPassWithoutLookupWhenRequestIsUnauthenticated() throws Exception {
    // given
    SecurityContextHolder.clearContext();

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(provider, never()).getCamundaAuthentication();
    verify(checker, never()).isActive(any(), any());
  }

  @Test
  void shouldPassWithoutLookupWhenAuthenticationIsAnonymous() throws Exception {
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(checker, never()).isActive(any(), any());
  }

  @Test
  void shouldPassWhenAuthenticationCannotBeResolved() throws Exception {
    // given
    when(provider.getCamundaAuthentication()).thenThrow(new IllegalStateException("boom"));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(checker, never()).isActive(any(), any());
  }

  @Nested
  class BrowserNavigation {

    private static final String CHROME_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Test
    void shouldRenderDeactivatedPageWithEscapedUsername() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("<b>jane</b>"));
      when(checker.isActive("default", "<b>jane</b>")).thenReturn(false);
      request.setRequestURI("/operate");
      request.addHeader("Accept", CHROME_ACCEPT);
      request.addHeader("Sec-Fetch-Mode", "navigate");

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
      assertThat(response.getContentAsString())
          .contains(
              "Your account is deactivated",
              "&lt;b&gt;jane&lt;/b&gt;",
              "Sign in again",
              "href=\"/blocked-login/reset\"")
          .doesNotContain("<b>jane</b>", "{{", "type=\"hidden\"", "/logout");
      verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldRenderMismatchPageWithSources() throws Exception {
      // given
      final var sources =
          new LoginSources(
              true,
              List.of(new Source("entra", "entra-reg", null), new Source("kc", "kc-reg", null)),
              Map.of());
      final var bound = new InactiveUserFilter(provider, checker, sources);
      when(provider.getCamundaAuthentication())
          .thenReturn(
              CamundaAuthentication.of(
                  b -> b.user("jane").claims(LoginSources.withLoginSource(Map.of(), "kc"))));
      when(checker.isActive("default", "jane")).thenReturn(true);
      when(checker.user("default", "jane"))
          .thenReturn(new StoredUser(true, "x", "entra", Map.of(), List.of()));
      request.setRequestURI("/operate");
      request.addHeader("Accept", "text/html");

      // when
      bound.doFilter(request, response, chain);

      // then
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getContentAsString())
          .contains("This sign-in can&#39;t be used", "Signed in with", "kc", "entra");
    }

    @Test
    void shouldEndTheSessionAndRenderThePageOnNavigation() throws Exception {
      // given
      final var session = new MockHttpSession();
      request.setSession(session);
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.setRequestURI("/operate");
      request.addHeader("Accept", CHROME_ACCEPT);

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(session.isInvalid()).isTrue();
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getContentAsString()).contains("Sign in again");
    }

    @Test
    void shouldEndTheSessionOnLoginMismatch() throws Exception {
      // given
      final var session = new MockHttpSession();
      request.setSession(session);
      final var sources =
          new LoginSources(true, List.of(new Source("entra", "entra-reg", null)), Map.of());
      final var bound = new InactiveUserFilter(provider, checker, sources);
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(true);
      when(checker.user("default", "jane"))
          .thenReturn(new StoredUser(true, "x", "entra", Map.of(), List.of()));
      request.setRequestURI("/operate");
      request.addHeader("Accept", "text/html");

      // when
      bound.doFilter(request, response, chain);

      // then
      assertThat(session.isInvalid()).isTrue();
      assertThat(response.getContentAsString()).contains("This sign-in can&#39;t be used");
    }

    @Test
    void shouldRespondUnauthorizedToXhrOnASession() throws Exception {
      // given
      final var session = new MockHttpSession();
      request.setSession(session);
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.setRequestURI("/operate/internal/users");
      request.addHeader("Accept", "application/json");

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(session.isInvalid()).isTrue();
      assertThat(response.getStatus()).isEqualTo(401);
      assertThat(response.getContentType()).startsWith("application/problem+json");
      assertThat(response.getContentAsString())
          .contains("\"status\":401", "The user is deactivated.");
    }

    @Test
    void shouldKeepForbiddenForBearerRequestsWithoutSession() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(request.getSession(false)).isNull();
      assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldNotBlockStaticAssets() throws Exception {
      // given
      final var session = new MockHttpSession();
      request.setSession(session);
      request.setRequestURI("/operate/assets/Decisions-CFfhmuCL.js");

      // when
      filter.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
      verify(checker, never()).isActive(any(), any());
      assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void shouldNotBlockAssetManifestButBlockJsonOutsideAssets() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.setRequestURI("/tasklist/assets/manifest.json");

      // when
      filter.doFilter(request, response, chain);
      final var other = new MockHttpServletRequest("GET", "/operate/data.json");
      final var otherResponse = new MockHttpServletResponse();
      filter.doFilter(other, otherResponse, chain);

      // then
      verify(chain).doFilter(request, response);
      assertThat(otherResponse.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldStillCheckApiPathsThatLookLikeFiles() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.setRequestURI("/v2/something.js");

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldKeepProblemJsonForXhr() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.addHeader("Accept", "application/json, text/plain, */*");

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void shouldKeepProblemJsonForApiPathEvenWithHtmlAccept() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.addHeader("Accept", CHROME_ACCEPT);
      request.setRequestURI("/v2/topology");

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void shouldKeepProblemJsonForNonNavigationFetch() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.addHeader("Accept", CHROME_ACCEPT);
      request.addHeader("Sec-Fetch-Mode", "cors");

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getContentType()).startsWith("application/problem+json");
    }
  }

  @Nested
  class LoginBinding {

    private final LoginSources sources =
        new LoginSources(
            true,
            List.of(
                new Source("entra", "entra-reg", "oid"),
                new Source("keycloak", "kc-reg", null),
                new Source("open", null, null)),
            Map.of());
    private final InactiveUserFilter bound = new InactiveUserFilter(provider, checker, sources);

    private StoredUser stored(final String source, final String externalId) {
      return new StoredUser(true, externalId, source, Map.of(), List.of());
    }

    private void login(final String username, final Map<String, Object> claims) {
      when(provider.getCamundaAuthentication())
          .thenReturn(CamundaAuthentication.of(b -> b.user(username).claims(claims)));
      when(checker.isActive("default", username)).thenReturn(true);
    }

    private void assertRejected() throws Exception {
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getContentAsString())
          .contains("The login does not match the provisioned user.");
      verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldPassWhenRegistrationAndBindingClaimMatch() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "entra", "oid", "guid-1"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", "guid-1"));

      // when
      bound.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenLoginCameThroughAnotherRegistration() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "keycloak", "oid", "guid-1"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", "guid-1"));

      // when
      bound.doFilter(request, response, chain);

      // then
      assertRejected();
    }

    @Test
    void shouldRejectWhenLoginHasNoSource() throws Exception {
      // given
      login("jane", Map.of("oid", "guid-1"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", "guid-1"));

      // when
      bound.doFilter(request, response, chain);

      // then
      assertRejected();
    }

    @Test
    void shouldRejectWhenBindingClaimDiffersFromExternalId() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "entra", "oid", "someone-else"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", "guid-1"));

      // when
      bound.doFilter(request, response, chain);

      // then
      assertRejected();
    }

    @Test
    void shouldRejectWhenBindingClaimIsMissing() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "entra"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", "guid-1"));

      // when
      bound.doFilter(request, response, chain);

      // then
      assertRejected();
    }

    @Test
    void shouldRejectWhenTheStoredUserHasNoExternalId() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "entra", "oid", "guid-1"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", null));

      // when
      bound.doFilter(request, response, chain);

      // then
      assertRejected();
    }

    @Test
    void shouldRejectUsersOfASourceThatIsNoLongerConfigured() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "entra", "oid", "guid-1"));
      when(checker.user("default", "jane")).thenReturn(stored("renamed", "guid-1"));

      // when
      bound.doFilter(request, response, chain);

      // then
      assertRejected();
    }

    @Test
    void shouldOnlyCheckTheRegistrationWithoutBindingClaim() throws Exception {
      // given
      login("jane", Map.of(LoginSources.LOGIN_SOURCE_CLAIM, "keycloak"));
      when(checker.user("default", "jane")).thenReturn(stored("keycloak", null));

      // when
      bound.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
    }

    @Test
    void shouldAcceptAnyLoginForASourceWithoutRegistrationAndClaim() throws Exception {
      // given
      login("jane", Map.of("sub", "x"));
      when(checker.user("default", "jane")).thenReturn(stored("open", null));

      // when
      bound.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotAffectUsersThatAreNotProvisioned() throws Exception {
      // given
      login("jane", Map.of("sub", "x"));
      when(checker.user("default", "jane")).thenReturn(StoredUser.UNKNOWN);

      // when
      bound.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
    }

    @Test
    void shouldSkipClients() throws Exception {
      // given
      when(provider.getCamundaAuthentication())
          .thenReturn(CamundaAuthentication.of(b -> b.clientId("client")));

      // when
      bound.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
      verify(checker, never()).user(any(), any());
    }

    @Test
    void shouldNotBindWhenScimIsDisabled() throws Exception {
      // given
      login("jane", Map.of("sub", "x"));
      when(checker.user("default", "jane")).thenReturn(stored("entra", "guid-1"));

      // when
      new InactiveUserFilter(provider, checker, LoginSources.disabled())
          .doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
    }
  }

  @Nested
  class BlockedLoginMarker {

    @Test
    void shouldSetMarkerWhenEndingTheSessionOfAnXhr() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.setSession(new MockHttpSession());
      request.setSecure(true);

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getStatus()).isEqualTo(401);
      final var cookie = response.getHeader("Set-Cookie");
      assertThat(cookie)
          .startsWith("CAMUNDA_LOGIN_BLOCKED=")
          .contains("Path=/", "Max-Age=600", "HttpOnly", "SameSite=Lax", "Secure");
      final var value = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
      assertThat(BlockedLogin.Marker.decode(value))
          .isEqualTo(new BlockedLogin.Marker(BlockedLogin.Variant.DEACTIVATED, "jane", null, null));
    }

    @Test
    void shouldSetMarkerWhenEndingTheSessionOfANavigation() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);
      request.setRequestURI("/operate");
      request.addHeader("Accept", "text/html");
      request.setSession(new MockHttpSession());

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getHeader("Set-Cookie"))
          .startsWith("CAMUNDA_LOGIN_BLOCKED=")
          .doesNotContain("Secure");
    }

    @Test
    void shouldNotSetMarkerWithoutSession() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(false);

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void shouldClearMarkerWhenAuthenticatedRequestPasses() throws Exception {
      // given
      when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
      when(checker.isActive("default", "jane")).thenReturn(true);
      request.setCookies(new Cookie("CAMUNDA_LOGIN_BLOCKED", "abc"));

      // when
      filter.doFilter(request, response, chain);

      // then
      verify(chain).doFilter(request, response);
      assertThat(response.getHeader("Set-Cookie"))
          .startsWith("CAMUNDA_LOGIN_BLOCKED=;")
          .contains("Max-Age=0", "Path=/");
    }
  }
}
