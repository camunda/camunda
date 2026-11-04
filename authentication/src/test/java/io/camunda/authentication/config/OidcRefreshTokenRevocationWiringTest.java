/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.camunda.authentication.config.controllers.OidcMockMvcTestHelper;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Boots the real OIDC webapp security chain assembled by {@link WebSecurityConfig} and logs a user
 * out through it, so this covers the wiring of {@link RefreshTokenRevocationLogoutHandler} rather
 * than the handler in isolation.
 *
 * <p>The wiring is the point. The handler is registered with {@code addLogoutHandler}, not as a
 * {@code LogoutSuccessHandler}, because {@code LogoutConfigurer} appends its own {@code
 * SecurityContextLogoutHandler} after everything added that way. Registered the other way it would
 * run after the session, and therefore the {@link OAuth2AuthorizedClient} holding the refresh
 * token, had been invalidated, and would find nothing to revoke. Nothing about that is visible in a
 * unit test of the handler: the request simply stops being sent, which is also what every fail-soft
 * path looks like.
 *
 * <p>The {@code revocation-endpoint-uri} property lets {@link ClientRegistrationFactory} populate
 * the {@code revocation_endpoint} provider metadata without needing a live IdP for discovery.
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
      "camunda.security.authentication.oidc.client-secret=the-client-secret",
      "camunda.security.authentication.oidc.redirect-uri=https://redirect.example.com/sso-callback",
      "camunda.security.authentication.oidc.authorization-uri=https://authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=https://token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=https://jwks.example.com",
      // Opted in explicitly, because 8.9 ships revocation off by default. Leaving it to the
      // default would make this test assert behaviour no deployment gets without asking for it.
      "camunda.security.authentication.oidc.revocation-enabled=true"
    })
public class OidcRefreshTokenRevocationWiringTest extends AbstractWebSecurityConfigTest {

  @RegisterExtension
  static WireMockExtension idp =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .build();

  private static final String REVOCATION_PATH = "/oauth2/revoke";
  private static final String REFRESH_TOKEN_VALUE = "the-session-refresh-token";

  @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @DynamicPropertySource
  static void registerRevocationEndpoint(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.revocation-endpoint-uri",
        () -> "http://localhost:" + idp.getPort() + REVOCATION_PATH);
  }

  @Test
  public void shouldRevokeTheSessionRefreshTokenOnLogout() {
    // given an OIDC-authenticated session holding a refresh token
    stubFor(post(urlEqualTo(REVOCATION_PATH)).willReturn(ok()));

    // when the user logs out
    final var result =
        mockMvcTester
            .post()
            .uri("https://localhost/logout")
            .with(oidcLoginHoldingARefreshToken())
            .with(csrf())
            .exchange();

    // then the IdP was asked to revoke that refresh token, authenticated as the configured client
    assertThat(result.getResponse().getStatus()).isBetween(200, 399);
    verify(
        revocationRequest()
            .withRequestBody(containsFormField("token", REFRESH_TOKEN_VALUE))
            .withRequestBody(containsFormField("token_type_hint", "refresh_token"))
            .withHeader("Authorization", equalTo(basicAuth("example", "the-client-secret"))));
  }

  /**
   * The second half of the wiring: a session whose authorized client never received a refresh token
   * has nothing to revoke, and must not produce a request. This is also what Spring's own {@code
   * oidcLogin()} produces by default, so a test written against it would pass without the handler
   * ever doing anything.
   */
  @Test
  public void shouldNotCallTheIdpWhenTheSessionHasNoRefreshToken() {
    // given an OIDC-authenticated session with no refresh token
    stubFor(post(urlEqualTo(REVOCATION_PATH)).willReturn(ok()));

    // when the user logs out
    final var result =
        mockMvcTester
            .post()
            .uri("https://localhost/logout")
            .with(OidcMockMvcTestHelper.oidcLogin(authorizedClientRepository))
            .with(csrf())
            .exchange();

    // then nothing was sent. The status assertion keeps this from passing on a logout that was
    // rejected before the chain ever ran.
    assertThat(result.getResponse().getStatus()).isBetween(200, 399);
    verify(0, revocationRequest());
  }

  private static RequestPatternBuilder revocationRequest() {
    return postRequestedFor(urlEqualTo(REVOCATION_PATH));
  }

  /**
   * Matches one field of an {@code application/x-www-form-urlencoded} body, so that a value which
   * happens to be a substring of another field cannot satisfy the match.
   */
  private static com.github.tomakehurst.wiremock.matching.StringValuePattern containsFormField(
      final String name, final String value) {
    return com.github.tomakehurst.wiremock.client.WireMock.matching(
        "(^|.*&)"
            + java.util.regex.Pattern.quote(name)
            + "="
            + java.util.regex.Pattern.quote(value)
            + "($|&.*)");
  }

  /**
   * Logs in against the registration the application's own {@code ClientRegistrationRepository}
   * holds, and stores an authorized client that actually carries a refresh token.
   *
   * <p>Both halves are load-bearing. Spring's {@code oidcLogin()} invents a throwaway registration
   * named {@code test}, which the real repository cannot resolve, so the handler would return
   * before sending anything; and the authorized client it builds carries no refresh token, which
   * the handler correctly treats as nothing to revoke. Either one alone turns this into a test that
   * passes whatever the chain does.
   */
  private RequestPostProcessor oidcLoginHoldingARefreshToken() {
    final ClientRegistration registration =
        ClientRegistration.withRegistrationId("oidc")
            .clientId("example")
            .clientSecret("the-client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/sso-callback")
            .authorizationUri("https://authorization.example.com")
            .tokenUri("https://token.example.com")
            .jwkSetUri("https://jwks.example.com")
            .userNameAttributeName("sub")
            .scope("openid")
            .build();
    return request -> {
      final var processed =
          org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
              .oidcLogin()
              .clientRegistration(registration)
              .postProcessRequest(request);
      authorizedClientRepository.saveAuthorizedClient(
          new OAuth2AuthorizedClient(
              registration,
              "principal",
              new OAuth2AccessToken(
                  OAuth2AccessToken.TokenType.BEARER,
                  "the-session-access-token",
                  Instant.now(),
                  Instant.now().plusSeconds(300)),
              new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, Instant.now())),
          null,
          processed,
          new MockHttpServletResponse());
      return processed;
    };
  }

  private static String basicAuth(final String clientId, final String clientSecret) {
    return "Basic "
        + java.util.Base64.getEncoder()
            .encodeToString(
                String.join(":", List.of(clientId, clientSecret))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
