/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.authentication.clusteradmin.ClusterAdminSecurityConfiguration;
import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cross-chain isolation ("leak trap") for the OIDC cluster-admin chain: an existing webapp OIDC
 * session cookie must not authenticate {@code /cluster/v2/**}, which is bearer-only. The mock
 * {@code JwtDecoder} from {@link WebSecurityOidcTestContext} is never invoked here — the request
 * carries a session, not a token — so no real OIDC provider is needed.
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
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com"
    })
public class ClusterAdminOidcChainIsolationTest extends AbstractWebSecurityConfigTest {

  @Autowired private JwtDecoder jwtDecoder;

  @Test
  public void shouldRejectWebappSessionOnClusterAdminEndpoint() {
    // given — a session carrying an authenticated principal that even holds the cluster-admin
    // authority, as a webapp OIDC login would leave behind (browser then holds the JSESSIONID)
    final SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            "webapp-user",
            null,
            List.of(
                new SimpleGrantedAuthority(
                    ClusterAdminSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY))));
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

    // when — that session cookie is presented to the cluster-admin API with no bearer token
    final MvcTestResult result =
        mockMvcTester
            .get()
            .session(session)
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_ENDPOINT)
            .exchange();

    // then — the bearer chain binds a request-scoped, session-free context repository, so the
    // session is ignored and the request is unauthenticated despite the cookie
    assertThat(result)
        .as("a webapp session cookie must not authenticate the OIDC cluster-admin API")
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldAllowPublicStatusEndpointWithoutToken() {
    // when — the carved-out public status endpoint is hit with no bearer token
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT)
            .exchange();

    // then — permitAll lets it through (dummy returns 200; the real endpoint returns 204)
    assertThat(result).hasStatus2xxSuccessful();
  }

  @Test
  public void shouldRejectMalformedBearerOnPublicStatusEndpoint() {
    // given — the decoder rejects this specific token, standing in for a real malformed/invalid
    // JWT (this context's JwtDecoder is a no-op mock that never validates real tokens)
    final String malformedToken = "not-a-valid-jwt";
    when(jwtDecoder.decode(eq(malformedToken))).thenThrow(new BadJwtException("malformed token"));

    // when — the public status endpoint is hit with that malformed bearer token
    final MvcTestResult result =
        mockMvcTester
            .get()
            .header("Authorization", "Bearer " + malformedToken)
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT)
            .exchange();

    // then — permitAll only waives a missing token; a bad one is still rejected by the bearer
    // filter before the authorization decision is reached
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldRequireTokenForSiblingOfPublicStatusEndpoint() {
    // when — a sibling of the public path (trailing slash) is hit with no bearer token
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri("https://localhost" + TestApiController.DUMMY_CLUSTER_ADMIN_STATUS_ENDPOINT + "/")
            .exchange();

    // then — only the exact path is public; everything else under /cluster/v2/** stays protected
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }
}
