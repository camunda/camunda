/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Regression test for camunda/camunda-security-library#484: {@code CamundaOidcLogoutSuccessHandler}
 * used to answer {@code POST /logout} with a 302 redirect to the IdP's {@code
 * end_session_endpoint}, even for {@code fetch()} callers. The webapps cannot follow a 302 into a
 * top-level cross-origin navigation from {@code fetch()}, so the IdP session was never terminated
 * and the user got silently logged back in.
 *
 * <p>Boots the real OIDC webapp security chain assembled by {@link WebSecurityConfig} (via
 * {@code @ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)}), so this verifies the
 * actual wiring of the CSL logout handler into the host application, not just the handler in
 * isolation. The {@code camunda.security.authentication.oidc.end-session-endpoint-uri} property
 * lets {@link io.camunda.security.spring.oidc.ScopedClientRegistrationFactory} populate the {@code
 * end_session_endpoint} provider metadata without needing a live IdP for discovery.
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=https://redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com",
      "camunda.security.authentication.oidc.end-session-endpoint-uri=https://idp.example.com/protocol/openid-connect/logout"
    })
public class OidcLogoutSuccessHandlerWiringTest extends AbstractWebSecurityConfigTest {

  private static final String LOGIN_HINT = "user@example.com";

  @Test
  public void shouldReturnJsonEndSessionUrlForFetchLogoutWhenEndSessionEndpointConfigured()
      throws IOException {
    // given an OIDC-authenticated fetch()/XHR caller (Sec-Fetch-Dest: empty) whose registration
    // has an end_session_endpoint

    // when it POSTs /logout
    final MvcTestResult result =
        mockMvcTester
            .post()
            .uri("https://localhost/logout")
            .header("Sec-Fetch-Dest", "empty")
            .accept(MediaType.APPLICATION_JSON)
            .with(oidcLoginWithLoginHint())
            .exchange();

    // then it gets a 200 JSON body with the IdP end-session URL, not a redirect
    assertThat(result)
        .hasStatus(HttpStatus.OK)
        .headers()
        .doesNotContainHeaders(HttpHeaders.LOCATION);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);

    final JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
    final MultiValueMap<String, String> query =
        queryParams(body.get("url").asText(), "idp.example.com", "/protocol/openid-connect/logout");
    assertThat(query.getFirst("id_token_hint")).isNotBlank();
    assertThat(
            URLDecoder.decode(
                Objects.requireNonNull(query.getFirst("logout_hint")), StandardCharsets.UTF_8))
        .isEqualTo(LOGIN_HINT);
    // CSL builds the primary chain's handler from SecurityPathAdapter#postLogoutRedirectPath (empty
    // prefix), so the IdP is told to send the browser back to the host's post-logout route.
    assertPrimaryPostLogoutRedirectUri(query);
  }

  @Test
  public void shouldReturnFoundRedirectForNonFetchLogoutWhenEndSessionEndpointConfigured() {
    // given the same OIDC-authenticated user, but performing a full-page navigation
    // (Sec-Fetch-Dest: document, HTML accept) rather than a fetch() call

    // when it POSTs /logout
    final MvcTestResult result =
        mockMvcTester
            .post()
            .uri("https://localhost/logout")
            .header("Sec-Fetch-Dest", "document")
            .accept(MediaType.TEXT_HTML)
            .with(oidcLoginWithLoginHint())
            .exchange();

    // then backward compatibility is preserved: a 302 redirect to the IdP end-session endpoint
    assertThat(result).hasStatus(HttpStatus.FOUND).containsHeader(HttpHeaders.LOCATION);
    final String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
    final MultiValueMap<String, String> query =
        queryParams(location, "idp.example.com", "/protocol/openid-connect/logout");
    assertThat(query.getFirst("id_token_hint")).isNotBlank();
    assertPrimaryPostLogoutRedirectUri(query);
  }

  private static MultiValueMap<String, String> queryParams(
      final String url, final String expectedHost, final String expectedPath) {
    final UriComponents parsed = UriComponentsBuilder.fromUriString(url).build();
    assertThat(parsed.getScheme()).isEqualTo("https");
    assertThat(parsed.getHost()).isEqualTo(expectedHost);
    assertThat(parsed.getPath()).isEqualTo(expectedPath);
    return parsed.getQueryParams();
  }

  private static void assertPrimaryPostLogoutRedirectUri(
      final MultiValueMap<String, String> query) {
    final UriComponents redirect =
        UriComponentsBuilder.fromUriString(
                URLDecoder.decode(
                    Objects.requireNonNull(query.getFirst("post_logout_redirect_uri")),
                    StandardCharsets.UTF_8))
            .build();
    assertThat(redirect.getHost()).isEqualTo("localhost");
    assertThat(redirect.getPath()).isEqualTo("/post-logout");
  }

  /**
   * Builds an OIDC login whose {@code authorizedClientRegistrationId} is {@code "oidc"} — the
   * registration id the app's real {@link
   * org.springframework.security.oauth2.client.registration.ClientRegistrationRepository} (built by
   * CSL from the {@code camunda.security.authentication.oidc.*} properties) uses, so {@code
   * CamundaOidcLogoutSuccessHandler} looks up the real registration (with its configured
   * end-session endpoint) rather than the throwaway registration used only to establish the mocked
   * security context. No {@link
   * org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository} wiring is
   * needed here: {@code determineTargetUrl} only reads the {@code Authentication} and the real
   * {@code ClientRegistrationRepository} bean, not an authorized client.
   */
  private RequestPostProcessor oidcLoginWithLoginHint() {
    final ClientRegistration registration =
        ClientRegistration.withRegistrationId("oidc")
            .clientId("example")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/sso-callback")
            .authorizationUri("https://authorization.example.com")
            .tokenUri("https://token.example.com")
            .jwkSetUri("https://jwks.example.com")
            .userNameAttributeName("sub")
            .scope("openid")
            .build();

    return SecurityMockMvcRequestPostProcessors.oidcLogin()
        .clientRegistration(registration)
        .idToken(token -> token.claim("login_hint", LOGIN_HINT));
  }
}
