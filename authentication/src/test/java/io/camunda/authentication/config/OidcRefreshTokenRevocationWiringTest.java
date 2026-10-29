/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Verifies that the Orchestration Cluster's own OIDC wiring reaches CSL's {@code
 * RefreshTokenRevocationLogoutHandler} (camunda/camunda-security-library ADR-0036), by logging out
 * through the real chain and watching what the IdP receives.
 *
 * <p>What this covers is the integration: that the host's beans and properties produce a chain
 * whose logout actually reaches the IdP with the session's refresh token. Asserting the handler
 * merely appears in some filter's handler list would pass even if the registration id, the
 * authorized-client lookup or the endpoint metadata were wrong, and each of those fails silently,
 * leaving a refresh token valid.
 *
 * <p>What it deliberately does <em>not</em> cover is the ordering of the handler against {@code
 * SecurityContextLogoutHandler}. {@code WebSecurityOidcTestContext} supplies an in-memory
 * authorized-client repository keyed by registration id and principal name, which ignores the
 * request and the session, so a token stays reachable after session invalidation and a reordering
 * regression would still pass here. That property is asserted structurally in CSL instead, by
 * {@code
 * ScopedWebappSecurityChainBuilderScopedTest#primaryOidcChainRevokesRefreshTokenBeforeInvalidatingTheSession}.
 *
 * <p>A real {@link HttpServer} stands in for the IdP: CSL builds the handler's HTTP client itself
 * and holds it statically, so there is no transport to replace, and the revocation endpoint is the
 * only seam. Its port is known only at runtime, hence {@link DynamicPropertySource}.
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
      "camunda.security.authentication.oidc.client-secret=example-secret",
      "camunda.security.authentication.oidc.redirect-uri=https://redirect.example.com/sso-callback",
      "camunda.security.authentication.oidc.authorization-uri=https://authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=https://token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=https://jwks.example.com"
    })
public class OidcRefreshTokenRevocationWiringTest extends AbstractWebSecurityConfigTest {

  private static final String REFRESH_TOKEN = "the-refresh-token";
  private static final String REGISTRATION_ID = "oidc";

  /** The {@code sub} claim {@code oidcLogin()} defaults to, and so the repository key. */
  private static final String PRINCIPAL_NAME = "user";

  private static final AtomicReference<String> RECEIVED_BODY = new AtomicReference<>();
  private static final AtomicReference<String> RECEIVED_AUTHORIZATION = new AtomicReference<>();
  private static HttpServer idp;

  @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @DynamicPropertySource
  static void revocationEndpoint(final DynamicPropertyRegistry registry) throws IOException {
    idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    idp.createContext(
        "/revoke",
        exchange -> {
          RECEIVED_BODY.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          RECEIVED_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    idp.start();
    registry.add(
        "camunda.security.authentication.oidc.revocation-endpoint-uri",
        () -> "http://127.0.0.1:" + idp.getAddress().getPort() + "/revoke");
  }

  @AfterAll
  static void stopIdp() {
    if (idp != null) {
      idp.stop(0);
    }
  }

  @BeforeEach
  void forgetPreviousRequest() {
    RECEIVED_BODY.set(null);
    RECEIVED_AUTHORIZATION.set(null);
  }

  @Test
  public void shouldRevokeTheSessionRefreshTokenAtTheIdpOnLogout() {
    // given an OIDC-authenticated user whose session holds a refresh token

    // when the webapp logs out
    final var result =
        mockMvcTester
            .post()
            .uri("https://localhost/logout")
            .header("Sec-Fetch-Dest", "empty")
            .with(oidcLoginHoldingARefreshToken())
            .exchange();

    // then logout itself succeeds. 204 because this registration publishes no end-session
    // endpoint, so there is no IdP URL for the webapp to navigate to afterwards.
    assertThat(result).hasStatus(HttpStatus.NO_CONTENT);

    // and the IdP was asked to revoke that token, as RFC 7009 specifies
    assertThat(RECEIVED_BODY.get()).as("the IdP received no revocation request").isNotNull();
    assertThat(formFields(RECEIVED_BODY.get()))
        .containsEntry("token", REFRESH_TOKEN)
        .containsEntry("token_type_hint", "refresh_token");

    // and it authenticated the request. The stand-in IdP answers 200 to anything, so without this
    // a regression that dropped the client credentials would still pass.
    assertThat(RECEIVED_AUTHORIZATION.get())
        .as("the revocation request was sent unauthenticated")
        .isEqualTo(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("example:example-secret".getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Logs in and leaves behind an authorized client that actually carries a refresh token.
   *
   * <p>Spring's {@code oidcLogin()} builds its client with an access token only, so on its own the
   * handler would correctly find nothing to revoke and this test would pass without ever exercising
   * the request it exists to prove. The client it created is replaced with an equivalent one
   * holding a refresh token, reusing its registration and principal name so the repository key
   * still matches the authentication the chain will see.
   */
  private RequestPostProcessor oidcLoginHoldingARefreshToken() {
    return request -> {
      final MockHttpServletRequest processed =
          SecurityMockMvcRequestPostProcessors.oidcLogin()
              .clientRegistration(configuredRegistration())
              .postProcessRequest(request);
      authorizedClientRepository.saveAuthorizedClient(
          new OAuth2AuthorizedClient(
              configuredRegistration(),
              PRINCIPAL_NAME,
              new OAuth2AccessToken(
                  OAuth2AccessToken.TokenType.BEARER,
                  "the-access-token",
                  Instant.now(),
                  Instant.now().plusSeconds(300)),
              new OAuth2RefreshToken(REFRESH_TOKEN, Instant.now())),
          null,
          processed,
          new MockHttpServletResponse());
      return processed;
    };
  }

  /**
   * The registration id must be the one the application configured. {@code oidcLogin()} otherwise
   * invents a throwaway registration named {@code test}, which the real {@code
   * ClientRegistrationRepository} cannot resolve, so the handler returns before sending anything
   * and this test passes while proving nothing.
   */
  private static ClientRegistration configuredRegistration() {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId("example")
        .clientSecret("example-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/sso-callback")
        .authorizationUri("https://authorization.example.com")
        .tokenUri("https://token.example.com")
        .jwkSetUri("https://jwks.example.com")
        .userNameAttributeName("sub")
        .scope("openid")
        .build();
  }

  private static Map<String, String> formFields(final String body) {
    return Arrays.stream(body.split("&"))
        .map(pair -> pair.split("=", 2))
        .collect(
            Collectors.toMap(
                pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "",
                (first, second) -> first));
  }
}
